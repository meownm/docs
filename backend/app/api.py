from __future__ import annotations

import os
import uuid
import json
import base64
from typing import AsyncIterator

from fastapi import APIRouter, UploadFile, File, HTTPException
from fastapi.responses import FileResponse, StreamingResponse

from app.settings import settings
from app.llm import ollama_chat_with_image, LLMUnavailableError
from app.events import event_bus

router = APIRouter()


@router.post("/passport/recognize")
async def recognize_passport(image: UploadFile = File(...)):
    image_bytes = await image.read()
    if not image_bytes:
        raise HTTPException(status_code=422, detail="Empty image file")

    try:
        request_id, llm_text = await ollama_chat_with_image(image_bytes)
    except LLMUnavailableError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"LLM error: {e}") from e

    return {
        "request_id": request_id,
        "raw": llm_text,
    }


@router.post("/passport/nfc")
async def passport_nfc(payload: dict):
    scan_id = str(uuid.uuid4())

    face_b64 = payload.get("face_image_b64")
    if isinstance(face_b64, str) and face_b64:
        os.makedirs(settings.files_dir, exist_ok=True)
        face_path = os.path.join(settings.files_dir, f"{scan_id}_face.jpg")
        try:
            with open(face_path, "wb") as f:
                f.write(base64.b64decode(face_b64))
        except Exception as e:
            raise HTTPException(status_code=400, detail=f"Invalid face_image_b64: {e}") from e

    # Публикуем событие в SSE
    await event_bus.publish({
        "type": "nfc_scan_success",
        "scan_id": scan_id,
    })

    return {"scan_id": scan_id}


@router.get("/nfc/{scan_id}/face.jpg")
async def get_nfc_face(scan_id: str):
    face_path = os.path.join(settings.files_dir, f"{scan_id}_face.jpg")
    if not os.path.exists(face_path):
        raise HTTPException(status_code=404, detail="Face image not found")
    return FileResponse(face_path, media_type="image/jpeg")


@router.get("/events")
async def events():
    async def event_stream() -> AsyncIterator[str]:
        async for event in event_bus.subscribe():
            yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
    )
