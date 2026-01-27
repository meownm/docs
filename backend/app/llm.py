from __future__ import annotations

import base64
import json
import uuid
from datetime import datetime
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
        "Task: read the passport image and extract MRZ (if present) and key fields.\n"
        "Return a concise JSON object with keys: mrz, fields.\n"
        "mrz: string or null\n"
        "fields: object with any of: surname, given_names, passport_number, nationality, "
        "date_of_birth, sex, date_of_expiry.\n"
        "If you cannot extract reliably, set mrz to null and fields to {}.\n"
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


async def ollama_chat_with_image(image_bytes: bytes) -> tuple[str, str]:
    """
    Реальный вызов Ollama vision-модели через /api/chat.
    Возвращает (request_id, assistant_text).
    """
    request_id = str(uuid.uuid4())
    model = settings.ollama_model
    ts_utc = datetime.utcnow().isoformat()
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
    prompt = build_prompt_for_passport_recognition()

    payload: dict[str, Any] = {
        "model": model,
        "stream": False,
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
        try:
            await _log_llm_request(
                request_id=request_id,
                model=model,
                input_json=input_json,
                output_json=output_json,
                success=success,
                error=error_text,
                ts_utc=ts_utc,
            )
        except Exception:
            pass
        raise LLMUnavailableError(error_text) from e
    except httpx.HTTPStatusError as e:
        # Ollama вернул валидный HTTP-ответ, но статус не 2xx
        body = e.response.text if e.response is not None else ""
        error_text = f"Ollama HTTP error: {e} body={body}"
        try:
            await _log_llm_request(
                request_id=request_id,
                model=model,
                input_json=input_json,
                output_json=output_json,
                success=success,
                error=error_text,
                ts_utc=ts_utc,
            )
        except Exception:
            pass
        raise RuntimeError(error_text) from e
    except Exception as e:
        if error_text is None:
            error_text = f"Ollama call failed: {e}"
        try:
            await _log_llm_request(
                request_id=request_id,
                model=model,
                input_json=input_json,
                output_json=output_json,
                success=success,
                error=error_text,
                ts_utc=ts_utc,
            )
        except Exception:
            pass
        raise RuntimeError(error_text) from e

    # Ожидаемый формат Ollama /api/chat:
    # { "message": { "role": "assistant", "content": "..." }, ... }
    msg = data.get("message") or {}
    content = msg.get("content")
    if not isinstance(content, str):
        error_text = f"Unexpected Ollama response format: {data}"
        output_json = json.dumps(data, ensure_ascii=False)
        try:
            await _log_llm_request(
                request_id=request_id,
                model=model,
                input_json=input_json,
                output_json=output_json,
                success=success,
                error=error_text,
                ts_utc=ts_utc,
            )
        except Exception:
            pass
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
