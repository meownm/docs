import base64
import binascii
import json
import os
from datetime import datetime, timezone
from uuid import uuid4

import aiosqlite
from fastapi import APIRouter, File, HTTPException, UploadFile
from fastapi.responses import FileResponse, StreamingResponse

from .events import Event, bus
from .schemas import ErrorInfo, MRZKeys, NFCPayload, NFCStoreResponse, RecognizeResponse
from .settings import settings
from .db import init_db


router = APIRouter()


@router.get("/events")
async def events():
    async def gen():
        async for e in bus.stream():
            # SSE format: event + data lines, blank line
            payload = json.dumps({"event_type": e.event_type, "data": e.data}, ensure_ascii=False)
            yield f"event: {e.event_type}\n"
            yield f"data: {payload}\n\n"

    return StreamingResponse(gen(), media_type="text/event-stream")


@router.post("/passport/recognize", response_model=RecognizeResponse)
async def recognize_passport(image: UploadFile = File(...)):
    data = await image.read()
    if not data:
        raise HTTPException(status_code=422, detail="Empty image")

    # Deferred: real Ollama call is in app.main. We import lazily to avoid cycles.
    from . import main as main_module

    request_id, result = await main_module.ollama_chat_with_image(data)

    parsed = (result or {}).get("parsed") or {}
    # schema check
    if all(k in parsed for k in ("document_number", "date_of_birth", "date_of_expiry")):
        mrz = MRZKeys(
            document_number=str(parsed["document_number"]),
            date_of_birth=str(parsed["date_of_birth"]),
            date_of_expiry=str(parsed["date_of_expiry"]),
        )
        return RecognizeResponse(request_id=request_id, mrz=mrz, raw=(result or {}).get("ollama_raw"), error=None)

    return RecognizeResponse(
        request_id=request_id,
        mrz=None,
        raw=(result or {}).get("ollama_raw"),
        error=ErrorInfo(error_code="SCHEMA_MISMATCH", message="LLM response does not match MRZ schema"),
    )


@router.post("/passport/nfc", response_model=NFCStoreResponse)
async def store_nfc(payload: NFCPayload):
    try:
        face_bytes = base64.b64decode(payload.face_image_b64, validate=True)
    except (binascii.Error, ValueError):
        raise HTTPException(status_code=422, detail="Invalid base64")

    await init_db()
    os.makedirs(settings.files_dir, exist_ok=True)

    scan_id = str(uuid4())
    face_path = os.path.join(settings.files_dir, f"{scan_id}_face.jpg")
    with open(face_path, "wb") as f:
        f.write(face_bytes)

    ts_utc = datetime.now(timezone.utc).isoformat()

    async with aiosqlite.connect(settings.db_path) as db:
        await db.execute(
            "INSERT INTO nfc_scans(scan_id, ts_utc, passport_json, face_image_path) VALUES(?,?,?,?)",
            (scan_id, ts_utc, json.dumps(payload.passport, ensure_ascii=False), face_path),
        )
        await db.commit()

    face_url = f"/api/nfc/{scan_id}/face.jpg"
    await bus.publish(Event(event_type="nfc_scan_success", data={"scan_id": scan_id, "face_image_url": face_url}))

    return NFCStoreResponse(scan_id=scan_id, status="stored")


@router.get("/nfc/{scan_id}/face.jpg")
async def get_face(scan_id: str):
    await init_db()
    async with aiosqlite.connect(settings.db_path) as db:
        cur = await db.execute("SELECT face_image_path FROM nfc_scans WHERE scan_id = ?", (scan_id,))
        row = await cur.fetchone()
    if not row:
        raise HTTPException(status_code=404, detail="Not found")
    face_path = row[0]
    if not os.path.exists(face_path):
        raise HTTPException(status_code=404, detail="File not found")
    return FileResponse(face_path, media_type="image/jpeg")
