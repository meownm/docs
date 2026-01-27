import json
from typing import Any, Dict, Tuple

import httpx
from fastapi import FastAPI
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles
from starlette.responses import StreamingResponse

from .api import router
from .events import bus
from .settings import settings
from .llm import build_prompt


app = FastAPI(title="FastAPI", version="0.1.0")

# API routes
app.include_router(router, prefix="/api")

# Static web UI (demo)
app.mount("/static", StaticFiles(directory=str(__import__("pathlib").Path(__file__).parent / "static")), name="static")


@app.get("/", response_class=HTMLResponse)
async def index():
    static_dir = __import__("pathlib").Path(__file__).parent / "static"
    return (static_dir / "index.html").read_text(encoding="utf-8")


@app.get("/api/events")
async def events():
    async def gen():
        async for e in bus.stream():
            # SSE format
            yield f"event: {e.event_type}\n"
            yield f"data: {json.dumps(e.data, ensure_ascii=False)}\n\n"
    return StreamingResponse(gen(), media_type="text/event-stream")


async def ollama_chat_with_image(image_bytes: bytes) -> Tuple[str, Dict[str, Any]]:
    """Call Ollama vision model with image and prompt. Kept simple; tests monkeypatch this."""
    request_id = "req-" + __import__("uuid").uuid4().hex[:8]
    prompt = build_prompt(settings.llm_lang)

    # Minimal Ollama chat payload for vision models.
    payload = {
        "model": settings.ollama_model,
        "messages": [
            {"role": "user", "content": prompt},
        ],
        # images as base64
        "images": [__import__("base64").b64encode(image_bytes).decode("ascii")],
        "stream": False,
    }

    async with httpx.AsyncClient(timeout=settings.ollama_timeout_seconds) as client:
        resp = await client.post(f"{settings.ollama_base_url.rstrip('/')}/api/chat", json=payload)
        resp.raise_for_status()
        data = resp.json()

    # Expect content to be JSON; keep raw for debugging.
    content = (data or {}).get("message", {}).get("content", "") or ""
    parsed = {}
    try:
        parsed = json.loads(content)
    except Exception:
        parsed = {}

    return request_id, {
        "parsed": parsed,
        "input_payload": {"prompt": prompt},
        "ollama_raw": data,
    }
