import base64
import json
import os
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict

from fastapi import Depends, FastAPI, File, HTTPException, UploadFile
from fastapi.responses import HTMLResponse, StreamingResponse, FileResponse
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware

from .settings import settings
from .db import init_db, get_db
from .llm import ollama_chat_with_image
from .schemas import ErrorInfo, MRZKeys, RecognizeResponse, NFCPayload, NFCStoreResponse
from .events import bus, Event


app = FastAPI(title="Passport Demo Backend", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Static (web UI)
STATIC_DIR = Path(__file__).parent / "static"
app.mount("/static", StaticFiles(directory=str(STATIC_DIR)), name="static")


@app.on_event("startup")
async def _startup() -> None:
    os.makedirs(settings.data_dir, exist_ok=True)
    os.makedirs(settings.files_dir, exist_ok=True)
    await init_db()


@app.get("/", response_class=HTMLResponse)
async def index() -> str:
    return (STATIC_DIR / "index.html").read_text(encoding="utf-8")


def _as_error(code: str, message: str) -> RecognizeResponse:
    return RecognizeResponse(request_id=str(uuid.uuid4()), error=ErrorInfo(error_code=code, message=message))


@app.post("/api/passport/recognize", response_model=RecognizeResponse)
async def recognize_passport(image: UploadFile = File(...), db=Depends(get_db)) -> RecognizeResponse:
    try:
        img = await image.read()
        if not img:
            raise HTTPException(status_code=422, detail="Empty image")

        request_id, meta = await ollama_chat_with_image(img)
        parsed = meta["parsed"]

        # persist LLM I/O
        log_id = str(uuid.uuid4())
        ts = datetime.now(timezone.utc).isoformat()
        success = 1
        error_text = None

        mrz: MRZKeys | None = None
        if isinstance(parsed, dict) and "error" in parsed:
            success = 0
            err = parsed.get("error") or {}
            error_text = json.dumps(err, ensure_ascii=False)
        else:
            try:
                mrz = MRZKeys(**parsed)  # type: ignore[arg-type]
            except Exception as e:
                success = 0
                error_text = f"schema_error: {e}"
                parsed = {"error": {"code": "SCHEMA_MISMATCH", "message": "Invalid fields from model"}, "raw": parsed}

        await db.execute(
            """INSERT INTO llm_logs (id, ts_utc, request_id, model, input_json, output_json, success, error)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                log_id,
                ts,
                request_id,
                settings.ollama_model,
                json.dumps(meta["input_payload"], ensure_ascii=False),
                json.dumps(meta["ollama_raw"], ensure_ascii=False),
                success,
                error_text,
            ),
        )
        await db.commit()

        if mrz is None:
            # error response
            err = parsed.get("error", {}) if isinstance(parsed, dict) else {}
            code = err.get("code", "RECOGNITION_FAILED")
            msg = err.get("message", "Recognition failed")
            return RecognizeResponse(request_id=request_id, error=ErrorInfo(error_code=code, message=msg), raw={"result": parsed})

        return RecognizeResponse(request_id=request_id, mrz=mrz, raw={"result": parsed})
    except HTTPException:
        raise
    except Exception as e:
        return _as_error("INTERNAL_ERROR", str(e))


@app.post("/api/passport/nfc", response_model=NFCStoreResponse)
async def store_nfc(payload: NFCPayload, db=Depends(get_db)) -> NFCStoreResponse:
    scan_id = str(uuid.uuid4())
    ts = datetime.now(timezone.utc).isoformat()

    # decode and store face image
    try:
        img_bytes = base64.b64decode(payload.face_image_b64)
    except Exception:
        raise HTTPException(status_code=422, detail="Invalid face_image_b64")

    face_path = Path(settings.files_dir) / f"{scan_id}_face.jpg"
    face_path.write_bytes(img_bytes)

    await db.execute(
        """INSERT INTO nfc_scans (scan_id, ts_utc, passport_json, face_image_path)
           VALUES (?, ?, ?, ?)""",
        (scan_id, ts, json.dumps(payload.passport, ensure_ascii=False), str(face_path)),
    )
    await db.commit()

    await bus.publish(
        Event(
            event_type="nfc_scan_success",
            data={"scan_id": scan_id, "face_image_url": f"/api/nfc/{scan_id}/face.jpg"},
        )
    )
    return NFCStoreResponse(scan_id=scan_id)


@app.get("/api/nfc/{scan_id}/face.jpg")
async def get_face(scan_id: str, db=Depends(get_db)) -> FileResponse:
    cur = await db.execute("SELECT face_image_path FROM nfc_scans WHERE scan_id = ?", (scan_id,))
    row = await cur.fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="Not found")
    path = row[0]
    return FileResponse(path, media_type="image/jpeg")


@app.get("/api/events")
async def sse_events():
    async def gen():
        async for e in bus.stream():
            # SSE format
            payload = json.dumps(e.data, ensure_ascii=False)
            yield f"event: {e.event_type}\n"
            yield f"data: {payload}\n\n"

    return StreamingResponse(gen(), media_type="text/event-stream")
