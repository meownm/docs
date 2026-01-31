from __future__ import annotations

import base64
import json
import uuid
from datetime import datetime, timezone
from typing import Any

import httpx

from app.db import get_db
from app.settings import settings


class LLMUnavailableError(RuntimeError):
    pass


def build_prompt_for_passport_recognition() -> str:
    # Строго прикладной промпт для OCR/MRZ. Можно усилить позже, но без «магии».
    # Сейчас цель: чтобы LLM реально отрабатывал и не падал.
    return (
        "You are a document recognition assistant.\n"
        "Task: read the passport image and extract MRZ keys required for BAC.\n"
        "Return ONLY a JSON object with the exact keys:\n"
        "document_number, date_of_birth, date_of_expiry\n"
        "Use YYMMDD for dates. No markdown, no extra text.\n"
    )


def build_prompt_for_passport_v2() -> str:
    return (
        "You are a document OCR assistant.\n"
        "Task: read the passport image and extract all available fields.\n"
        "Return ONLY a JSON object in the structure:\n"
        "{\n"
        '  "fields": {\n'
        '    "document_number": {"value": "...", "confidence": 0.0, "text_type": "printed|handwritten|unknown", "language": "ru|en|...", "zones": [{"page": 0, "x": 0.0, "y": 0.0, "w": 0.0, "h": 0.0}]},\n'
        '    "document_series": {"value": "...", "confidence": 0.0},\n'
        '    "last_name": {"value": "...", "confidence": 0.0},\n'
        '    "first_name": {"value": "...", "confidence": 0.0},\n'
        '    "middle_name": {"value": "...", "confidence": 0.0},\n'
        '    "date_of_birth": {"value": "YYYY-MM-DD", "confidence": 0.0},\n'
        '    "place_of_birth": {"value": "...", "confidence": 0.0},\n'
        '    "gender": {"value": "...", "confidence": 0.0},\n'
        '    "nationality": {"value": "...", "confidence": 0.0},\n'
        '    "date_of_issue": {"value": "YYYY-MM-DD", "confidence": 0.0},\n'
        '    "date_of_expiry": {"value": "YYYY-MM-DD", "confidence": 0.0},\n'
        '    "issuing_authority": {"value": "...", "confidence": 0.0},\n'
        '    "issuing_country": {"value": "...", "confidence": 0.0},\n'
        '    "personal_number": {"value": "...", "confidence": 0.0}\n'
        "  },\n"
        '  "mrz": {\n'
        '    "lines": ["...", "..."],\n'
        '    "document_number": "...",\n'
        '    "date_of_birth": "YYYY-MM-DD",\n'
        '    "date_of_expiry": "YYYY-MM-DD",\n'
        '    "confidence": 0.0\n'
        "  },\n"
        '  "zones": [{"field": "document_number", "page": 0, "x": 0.0, "y": 0.0, "w": 0.0, "h": 0.0}],\n'
        '  "checks": [{"code": "...", "status": "ok|warning|error", "message": "..."}],\n'
        '  "raw_text": "..."\n'
        "}\n"
        "Use 0..1 confidence scores. Be pessimistic. Do NOT guess values.\n"
        "If you are not sure, set value to null and confidence to 0.0.\n"
        "No markdown, no extra text.\n"
    )


async def _log_llm_request(
    *,
    request_id: str,
    model: str,
    input_json: str,
    output_json: str | None,
    success: int,
    error: str | None,
    ts_utc: str,
) -> None:
    async with get_db() as conn:
        await conn.execute(
            """
            INSERT INTO llm_logs (
                id,
                ts_utc,
                request_id,
                model,
                input_json,
                output_json,
                success,
                error
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                str(uuid.uuid4()),
                ts_utc,
                request_id,
                model,
                input_json,
                output_json,
                success,
                error,
            ),
        )
        await conn.commit()


async def _ollama_chat_with_image_prompt(image_bytes: bytes, prompt: str) -> tuple[str, str]:
    """
    Реальный вызов Ollama vision-модели через /api/chat.
    Возвращает (request_id, assistant_text).
    """
    request_id = str(uuid.uuid4())
    model = settings.ollama_model
    ts_utc = datetime.now(timezone.utc).isoformat()
    input_json = ""
    output_json = None
    success = 0
    error_text = None

    if not image_bytes:
        error_text = "image_bytes is empty"
        input_json = json.dumps({"error": error_text}, ensure_ascii=False)
        await _log_llm_request(
            request_id=request_id,
            model=model,
            input_json=input_json,
            output_json=output_json,
            success=success,
            error=error_text,
            ts_utc=ts_utc,
        )
        raise ValueError(error_text)

    image_b64 = base64.b64encode(image_bytes).decode("ascii")
    payload: dict[str, Any] = {
        "model": model,
        "stream": False,
        "format": "json",
        "messages": [
            {
                "role": "user",
                "content": prompt,
                "images": [image_b64],
            }
        ],
    }
    input_json = json.dumps(payload, ensure_ascii=False)

    timeout = httpx.Timeout(settings.ollama_timeout_sec)

    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            r = await client.post(f"{settings.ollama_base_url}/api/chat", json=payload)
            r.raise_for_status()
            data = r.json()
    except httpx.ConnectError as e:
        error_text = f"Ollama is not reachable at {settings.ollama_base_url}"
        await _log_llm_request(
            request_id=request_id,
            model=model,
            input_json=input_json,
            output_json=output_json,
            success=success,
            error=error_text,
            ts_utc=ts_utc,
        )
        raise LLMUnavailableError(error_text) from e
    except httpx.HTTPStatusError as e:
        # Ollama вернул валидный HTTP-ответ, но статус не 2xx
        body = e.response.text if e.response is not None else ""
        output_json = body or None
        error_text = f"Ollama HTTP error: {e} body={body}"
        await _log_llm_request(
            request_id=request_id,
            model=model,
            input_json=input_json,
            output_json=output_json,
            success=success,
            error=error_text,
            ts_utc=ts_utc,
        )
        raise RuntimeError(error_text) from e
    except Exception as e:
        if error_text is None:
            error_text = f"Ollama call failed: {e}"
        await _log_llm_request(
            request_id=request_id,
            model=model,
            input_json=input_json,
            output_json=output_json,
            success=success,
            error=error_text,
            ts_utc=ts_utc,
        )
        raise RuntimeError(error_text) from e

    # Ожидаемый формат Ollama /api/chat:
    # { "message": { "role": "assistant", "content": "..." }, ... }
    msg = data.get("message") or {}
    content = msg.get("content")
    if not isinstance(content, str):
        error_text = f"Unexpected Ollama response format: {data}"
        output_json = json.dumps(data, ensure_ascii=False)
        await _log_llm_request(
            request_id=request_id,
            model=model,
            input_json=input_json,
            output_json=output_json,
            success=success,
            error=error_text,
            ts_utc=ts_utc,
        )
        raise RuntimeError(error_text)

    output_json = json.dumps(data, ensure_ascii=False)
    success = 1
    await _log_llm_request(
        request_id=request_id,
        model=model,
        input_json=input_json,
        output_json=output_json,
        success=success,
        error=error_text,
        ts_utc=ts_utc,
    )
    return request_id, content


async def ollama_chat_with_image(image_bytes: bytes) -> tuple[str, str]:
    return await _ollama_chat_with_image_prompt(
        image_bytes,
        build_prompt_for_passport_recognition(),
    )


async def ollama_chat_with_image_v2(image_bytes: bytes) -> tuple[str, str]:
    return await _ollama_chat_with_image_prompt(
        image_bytes,
        build_prompt_for_passport_v2(),
    )
