import base64
import os
import uuid
from typing import Any, Dict, Optional

from fastapi import APIRouter, File, HTTPException, UploadFile
from fastapi.responses import FileResponse

from .schemas import ErrorInfo, MRZKeys, NFCPayload, NFCStoreResponse, RecognizeResponse
from .settings import settings

router = APIRouter()


def _ensure_dirs() -> None:
    os.makedirs(settings.files_dir, exist_ok=True)


@router.post("/passport/recognize", response_model=RecognizeResponse)
async def recognize_passport(image: UploadFile = File(...)) -> RecognizeResponse:
    """Recognize passport MRZ/BAC keys from a passport photo."""
    image_bytes = await image.read()
    if not image_bytes:
        # Align with tests: empty image should be treated as validation error (422).
        raise HTTPException(status_code=422, detail="Empty image")

    # Import lazily to keep composition root in main.py and allow tests to monkeypatch it.
    from . import main as main_module

    request_id, result = await main_module.ollama_chat_with_image(image_bytes)

    parsed: Dict[str, Any] = (result or {}).get("parsed") or {}
    mrz: Optional[MRZKeys] = None
    error: Optional[ErrorInfo] = None

    try:
        mrz = MRZKeys(**parsed)
    except Exception:
        # Schema mismatch is a canonical, stable error used by tests.
        error = ErrorInfo(error_code="SCHEMA_MISMATCH", message="LLM response schema mismatch")

    return RecognizeResponse(
        request_id=request_id,
        mrz=mrz,
        raw=result,
        error=error,
    )


@router.post("/passport/nfc", response_model=NFCStoreResponse)
async def store_nfc(payload: NFCPayload) -> NFCStoreResponse:
    """Store NFC scan payload + face image (base64) and make it retrievable by scan_id."""
    _ensure_dirs()
    scan_id = str(uuid.uuid4())

    try:
        face_bytes = base64.b64decode(payload.face_image_b64, validate=True)
    except Exception:
        raise HTTPException(status_code=422, detail="Invalid face_image_b64")

    face_path = os.path.join(settings.files_dir, f"{scan_id}_face.jpg")
    with open(face_path, "wb") as f:
        f.write(face_bytes)

    # Publish event for web UI (SSE).
    try:
        from .events import Event, bus
        await bus.publish(
            Event(
                event_type="nfc_scan_success",
                data={
                    "scan_id": scan_id,
                    "face_image_url": f"/api/nfc/{scan_id}/face.jpg",
                    "passport": payload.passport,
                },
            )
        )
    except Exception:
        # Observability: eventing is best-effort for demo; storage must succeed even if SSE fails.
        pass

    return NFCStoreResponse(scan_id=scan_id, status="stored")


@router.get("/nfc/{scan_id}/face.jpg")
async def get_face_image(scan_id: str):
    _ensure_dirs()
    face_path = os.path.join(settings.files_dir, f"{scan_id}_face.jpg")
    if not os.path.exists(face_path):
        raise HTTPException(status_code=404, detail="Not found")
    return FileResponse(face_path, media_type="image/jpeg")
