import base64
import json
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, Tuple

import httpx
from .settings import settings


def build_prompt(lang: str) -> str:
    # Строго просим JSON. Поля — только то, что нужно для BAC (MRZ keys).
    # Для демонстрации: document_number, date_of_birth, date_of_expiry.
    if lang.lower().startswith("ru"):
        return (
            "Ты распознаёшь загранпаспорт (eMRTD). "
            "Нужно извлечь параметры для NFC (BAC / MRZ keys): "
            "номер документа, дата рождения, дата окончания срока действия. "
            "Верни СТРОГО JSON без пояснений по схеме: "
            "{\"document_number\":\"...\",\"date_of_birth\":\"YYYY-MM-DD\",\"date_of_expiry\":\"YYYY-MM-DD\"}. "
            "Если распознать нельзя, верни JSON: {\"error\":{\"code\":\"MRZ_NOT_FOUND\",\"message\":\"...\"}}."
        )
    return """
    You parse a passport (eMRTD).
    Extract BAC/MRZ key inputs: document number, date of birth, date of expiry.

    Return STRICT JSON only:

    {
    "document_number": "...",
    "date_of_birth": "YYYY-MM-DD",
    "date_of_expiry": "YYYY-MM-DD"
    }

    If not possible, return:

    {
    "error": {
        "code": "MRZ_NOT_FOUND",
        "message": "..."
    }
    }
    """


async def ollama_chat_with_image(image_bytes: bytes) -> Tuple[str, Dict[str, Any]]:
    request_id = str(uuid.uuid4())
    img_b64 = base64.b64encode(image_bytes).decode("ascii")

    payload = {
        "model": settings.ollama_model,
        "messages": [
            {"role": "system", "content": build_prompt(settings.llm_lang)},
            {
                "role": "user",
                "content": "Распознай по фото.",
                "images": [img_b64],
            },
        ],
        "stream": False,
        "format": "json",
        # keep deterministic for extraction
        "options": {"temperature": 0.1},
    }

    url = settings.ollama_base_url.rstrip("/") + "/api/chat"
    timeout = httpx.Timeout(settings.ollama_timeout_seconds)

    async with httpx.AsyncClient(timeout=timeout) as client:
        r = await client.post(url, json=payload)
        r.raise_for_status()
        data = r.json()

    # Ollama chat returns message.content string; with format=json it should be JSON string
    content = data.get("message", {}).get("content", "")
    parsed: Dict[str, Any]
    try:
        parsed = json.loads(content) if isinstance(content, str) else {"raw": content}
    except Exception:
        parsed = {"error": {"code": "LLM_BAD_JSON", "message": "Model returned non-JSON content"}, "raw": content}

    meta = {
        "request_id": request_id,
        "ts_utc": datetime.now(timezone.utc).isoformat(),
        "model": settings.ollama_model,
        "ollama_raw": data,
        "parsed": parsed,
        "input_payload": payload,
    }
    return request_id, meta
