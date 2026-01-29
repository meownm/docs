from __future__ import annotations

import json
import re
import os
import uuid
import base64
import binascii
import io
from datetime import datetime, timezone
from typing import AsyncIterator

from fastapi import APIRouter, UploadFile, File, HTTPException
from fastapi.responses import FileResponse, StreamingResponse

from app.settings import settings
from app.llm import ollama_chat_with_image, LLMUnavailableError
from app.events import event_bus
from app.db import get_db
from app.schemas import AppErrorLogIn


router = APIRouter()


# ============================================================
# MRZ extraction (канон мобилки)
# ============================================================

def parse_date_to_yymmdd(value: str | None) -> str | None:
    if value is None:
        return None
    normalized = value.strip()
    if not normalized:
        return None
    if re.fullmatch(r"\d{6}", normalized):
        return normalized
    try:
        if re.fullmatch(r"\d{8}", normalized):
            parsed = datetime.strptime(normalized, "%Y%m%d")
            return parsed.strftime("%y%m%d")
        if re.fullmatch(r"\d{4}-\d{2}-\d{2}", normalized):
            parsed = datetime.strptime(normalized, "%Y-%m-%d")
            return parsed.strftime("%y%m%d")
    except ValueError:
        return None
    return None


def normalize_mrz_container(container: dict) -> dict:
    updated = dict(container)
    if "document_number" in updated and updated["document_number"] is not None:
        document_number = str(updated["document_number"]).strip()
        document_number = re.sub(r"\s+", "", document_number)
        updated["document_number"] = document_number.upper()
    for key in ("date_of_birth", "date_of_expiry"):
        if key in updated:
            normalized = parse_date_to_yymmdd(str(updated[key]))
            if normalized is not None:
                updated[key] = normalized
    return updated


def extract_mrz(llm_text: str) -> dict | None:
    """
    Извлекает MRZ-поля, ожидаемые мобильным клиентом.

    Поддержка:
    - JSON ответа модели (плоский или { "mrz": {...} })
    - fallback через regex по тексту
    """
    # --- JSON ---
    try:
        data = json.loads(llm_text)
        candidates = []
        if isinstance(data.get("mrz"), dict):
            candidates.append(data["mrz"])
        if isinstance(data.get("fields"), dict):
            fields = data["fields"]
            candidates.append({
                "document_number": fields.get("document_number") or fields.get("passport_number"),
                "date_of_birth": fields.get("date_of_birth"),
                "date_of_expiry": fields.get("date_of_expiry"),
            })
        if isinstance(data, dict):
            candidates.append(data)

        for src in candidates:
            if all(src.get(k) for k in ("document_number", "date_of_birth", "date_of_expiry")):
                date_of_birth = parse_date_to_yymmdd(str(src["date_of_birth"]))
                date_of_expiry = parse_date_to_yymmdd(str(src["date_of_expiry"]))
                if date_of_birth is None or date_of_expiry is None:
                    continue
                return normalize_mrz_container({
                    "document_number": str(src["document_number"]),
                    "date_of_birth": date_of_birth,
                    "date_of_expiry": date_of_expiry,
                })
    except Exception:
        pass

    # --- Regex fallback ---
    patterns = {
        "document_number": r"document[_\s]?number[:=]\s*([A-Z0-9<]+)",
        "date_of_birth": r"date[_\s]?of[_\s]?birth[:=]\s*([0-9]{6}|[0-9]{8}|[0-9]{4}-[0-9]{2}-[0-9]{2})",
        "date_of_expiry": r"date[_\s]?of[_\s]?expiry[:=]\s*([0-9]{6}|[0-9]{8}|[0-9]{4}-[0-9]{2}-[0-9]{2})",
    }

    result: dict[str, str] = {}
    for key, pattern in patterns.items():
        match = re.search(pattern, llm_text, re.IGNORECASE)
        if not match:
            return None
        result[key] = match.group(1)

    normalized = normalize_mrz_container(result)
    if any(parse_date_to_yymmdd(str(normalized.get(key))) is None for key in ("date_of_birth", "date_of_expiry")):
        return None
    return normalized


JPEG_SOI = b"\xff\xd8"
JPEG_EOI = b"\xff\xd9"
JP2_MAGIC = b"\x00\x00\x00\x0cjP  \r\n\x87\n"
JPEG_TAIL_BYTES = 64


def _detect_image_format(image_bytes: bytes) -> str:
    if image_bytes.startswith(JPEG_SOI):
        eoi_index = image_bytes.rfind(JPEG_EOI)
        if eoi_index != -1:
            tail_length = len(image_bytes) - (eoi_index + len(JPEG_EOI))
            if tail_length <= JPEG_TAIL_BYTES:
                return "jpeg"
    if image_bytes.startswith(JP2_MAGIC):
        return "jp2"
    return "unknown"


def _convert_jp2_to_jpeg(image_bytes: bytes) -> bytes | None:
    try:
        from PIL import Image, UnidentifiedImageError
    except ImportError:
        return None

    try:
        image = Image.open(io.BytesIO(image_bytes))
        rgb_image = image.convert("RGB")
        buffer = io.BytesIO()
        rgb_image.save(buffer, format="JPEG")
        jpeg_bytes = buffer.getvalue()
        if not jpeg_bytes:
            return None
        return jpeg_bytes
    except (UnidentifiedImageError, OSError, ValueError):
        return None


def _ensure_jpeg_bytes(image_bytes: bytes) -> bytes | None:
    image_format = _detect_image_format(image_bytes)
    if image_format == "jpeg":
        return image_bytes
    if image_format == "jp2":
        return _convert_jp2_to_jpeg(image_bytes)
    return None


# ============================================================
# Passport recognition
# ============================================================

async def _recognize_impl(image: UploadFile):
    image_bytes = await image.read()
    if not image_bytes:
        return {"error": "Empty image file"}

    try:
        _request_id, llm_text = await ollama_chat_with_image(image_bytes)
    except LLMUnavailableError as e:
        return {"error": str(e)}
    except Exception as e:
        return {"error": f"LLM error: {e}"}

    mrz = extract_mrz(llm_text)
    if not mrz:
        return {"error": "MRZ not found in recognition result"}

    return mrz


@router.post("/recognize")
async def recognize_passport(image: UploadFile = File(...)):
    return await _recognize_impl(image)


# ============================================================
# NFC
# ============================================================

async def _nfc_impl(payload: dict):
    scan_id = str(uuid.uuid4())

    passport = payload.get("passport")
    if passport is None and isinstance(payload.get("mrz"), dict):
        passport = {"mrz": payload.get("mrz")}
    if not isinstance(passport, dict) or not passport:
        raise HTTPException(status_code=422, detail="Invalid passport")

    face_b64 = payload.get("face_image_b64")
    if not isinstance(face_b64, str) or not face_b64:
        raise HTTPException(status_code=422, detail="Invalid face_image_b64")

    try:
        face_bytes = base64.b64decode(face_b64, validate=True)
    except (binascii.Error, ValueError) as e:
        raise HTTPException(status_code=422, detail=f"Invalid face_image_b64: {e}")

    jpeg_bytes = _ensure_jpeg_bytes(face_bytes)
    if jpeg_bytes is None:
        raise HTTPException(
            status_code=422,
            detail="Invalid face_image_b64: expected JPEG or JP2 convertible to JPEG",
        )

    passport = dict(passport)
    if "mrz" in passport and isinstance(passport.get("mrz"), dict):
        passport["mrz"] = normalize_mrz_container(passport["mrz"])
    elif all(key in passport for key in ("document_number", "date_of_birth", "date_of_expiry")):
        passport["mrz"] = normalize_mrz_container({
            "document_number": passport.get("document_number"),
            "date_of_birth": passport.get("date_of_birth"),
            "date_of_expiry": passport.get("date_of_expiry"),
        })
    if isinstance(payload.get("mrz"), dict) and "mrz" not in passport:
        passport["mrz"] = normalize_mrz_container(payload.get("mrz"))
    passport = normalize_mrz_container(passport)

    ts_utc = datetime.now(timezone.utc).isoformat()
    os.makedirs(settings.files_dir, exist_ok=True)
    face_path = os.path.join(settings.files_dir, f"{scan_id}_face.jpg")
    with open(face_path, "wb") as f:
        f.write(jpeg_bytes)

    async with get_db() as conn:
        await conn.execute(
            """
            INSERT INTO nfc_scans (
                scan_id,
                ts_utc,
                passport_json,
                face_image_path
            )
            VALUES (?, ?, ?, ?)
            """,
            (
                scan_id,
                ts_utc,
                json.dumps(passport, ensure_ascii=False),
                face_path,
            ),
        )
        await conn.commit()

    face_image_url = f"/api/nfc/{scan_id}/face.jpg"
    normalized_response = {
        "scan_id": scan_id,
        "face_image_url": face_image_url,
        "passport": passport,
    }

    await event_bus.publish({
        "type": "nfc_scan_success",
        "scan_id": scan_id,
        "face_image_url": face_image_url,
        "passport": passport,
    })

    return normalized_response


@router.post("/nfc")
async def passport_nfc(payload: dict):
    return await _nfc_impl(payload)


@router.get("/nfc/{scan_id}/face.jpg")
async def get_nfc_face(scan_id: str):
    face_path = os.path.join(settings.files_dir, f"{scan_id}_face.jpg")
    if not os.path.exists(face_path):
        raise HTTPException(status_code=404, detail="Face image not found")
    return FileResponse(face_path, media_type="image/jpeg")


# ============================================================
# SSE events
# ============================================================

async def _event_stream() -> AsyncIterator[str]:
    async for event in event_bus.subscribe():
        event_type = event.get("type", "message")
        data = json.dumps(event, ensure_ascii=False)
        yield f"event: {event_type}\ndata: {data}\n\n"


# --- Канон мобилки ---
@router.get("/events")
async def events_mobile():
    return StreamingResponse(
        _event_stream(),
        media_type="text/event-stream",
    )


# ============================================================
# App error logs
# ============================================================

async def _store_error_log(payload: AppErrorLogIn) -> dict:
    ts_utc = payload.ts_utc or datetime.now(timezone.utc).isoformat()
    context_json = (
        json.dumps(payload.context_json, ensure_ascii=False)
        if payload.context_json is not None
        else None
    )

    async with get_db() as db:
        cursor = await db.execute(
            """
            INSERT INTO app_error_logs (
                ts_utc,
                platform,
                app_version,
                error_message,
                stacktrace,
                context_json,
                user_agent,
                device_info,
                request_id
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                ts_utc,
                payload.platform,
                payload.app_version,
                payload.error_message,
                payload.stacktrace,
                context_json,
                payload.user_agent,
                payload.device_info,
                payload.request_id,
            ),
        )
        await db.commit()

    return {"status": "stored", "id": cursor.lastrowid}


# --- Канон мобилки ---
@router.post("/errors")
async def log_error_mobile(payload: AppErrorLogIn):
    return await _store_error_log(payload)
