from __future__ import annotations

import base64
import uuid
from typing import Any

import httpx

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


async def ollama_chat_with_image(image_bytes: bytes) -> tuple[str, str]:
    """
    Реальный вызов Ollama vision-модели через /api/chat.
    Возвращает (request_id, assistant_text).
    """
    request_id = str(uuid.uuid4())

    if not image_bytes:
        raise ValueError("image_bytes is empty")

    image_b64 = base64.b64encode(image_bytes).decode("ascii")
    prompt = build_prompt_for_passport_recognition()

    payload: dict[str, Any] = {
        "model": settings.ollama_model,
        "stream": False,
        "messages": [
            {
                "role": "user",
                "content": prompt,
                "images": [image_b64],
            }
        ],
    }

    timeout = httpx.Timeout(settings.ollama_timeout_sec)

    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            r = await client.post(f"{settings.ollama_base_url}/api/chat", json=payload)
            r.raise_for_status()
            data = r.json()
    except httpx.ConnectError as e:
        raise LLMUnavailableError(
            f"Ollama is not reachable at {settings.ollama_base_url}"
        ) from e
    except httpx.HTTPStatusError as e:
        # Ollama вернул валидный HTTP-ответ, но статус не 2xx
        body = e.response.text if e.response is not None else ""
        raise RuntimeError(f"Ollama HTTP error: {e} body={body}") from e
    except Exception as e:
        raise RuntimeError(f"Ollama call failed: {e}") from e

    # Ожидаемый формат Ollama /api/chat:
    # { "message": { "role": "assistant", "content": "..." }, ... }
    msg = data.get("message") or {}
    content = msg.get("content")
    if not isinstance(content, str):
        raise RuntimeError(f"Unexpected Ollama response format: {data}")

    return request_id, content
