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


# -------------------------
# Helpers
# -------------------------

async def _recognize_impl(image: UploadFile):
    image_bytes = await image.read()
    if not image_bytes:
        raise HTTPException(status_code=422, detail="Empty image file")

    try:
        request_id, llm_text = await ollama_chat_with_image(image_bytes)
    except LLMUnavailableError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"LLM error: {e}") from e

    # ВАЖНО: сейчас возвращаем сырой ответ.
    # Если нужно строго под MRZKeys мобилки — скажи, сделаю парсер в отдельном шаге.
    return {
        "request_id": request_id,
        "raw": llm_text,
    }


# -------------------------
# Passport recognize
# -------------------------

# Текущий (как в твоём backend)
@router.post("/passport/recognize")
async def recognize_passport(image: UploadFile = File(...)):
    return await _recognize_impl(image)

# Канон мобилки: POST /recognize с multipart полем "image"
@router.post("/recognize")
async def recognize_mobile(image: UploadFile = File(...)):
    return await _recognize_impl(image)


# -------------------------
# NFC
# -------------------------

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

    await event_bus.publish({
        "type": "nfc_scan_success",
        "scan_id": scan_id,
    })

    return {"scan_id": scan_id}

# Канон мобилки: POST /nfc
@router.post("/nfc")
async def passport_nfc_mobile(payload: dict):
    return await passport_nfc(payload)


@router.get("/nfc/{scan_id}/face.jpg")
async def get_nfc_face(scan_id: str):
    face_path = os.path.join(settings.files_dir, f"{scan_id}_face.jpg")
    if not os.path.exists(face_path):
        raise HTTPException(status_code=404, detail="Face image not found")
    return FileResponse(face_path, media_type="image/jpeg")


# -------------------------
# SSE events
# -------------------------

@router.get("/events")
async def events():
    async def event_stream() -> AsyncIterator[str]:
        async for event in event_bus.subscribe():
            yield f"data: {json.dumps(event, ensure_ascii=False)}\n\n"

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
    )
