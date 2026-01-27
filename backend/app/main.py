from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from .api import router


app = FastAPI(title="FastAPI", version="0.1.0")

# API
app.include_router(router, prefix="/api")

# Web UI (served from backend/app/static)
app.mount("/", StaticFiles(directory="app/static", html=True), name="static")


async def init_db() -> None:
    # re-export for tests / tooling
    from .db import init_db as _init_db
    await _init_db()


async def ollama_chat_with_image(image_bytes: bytes):
    # This function is monkeypatched in tests.
    # In real usage it should call Ollama using settings.ollama_base_url / model and return:
    # (request_id, {"parsed": {...}, "input_payload": {...}, "ollama_raw": {...}})
    raise NotImplementedError("ollama_chat_with_image must be implemented or monkeypatched in tests")
