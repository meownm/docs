from __future__ import annotations

import os

from fastapi import FastAPI
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles

from app.api import router


app = FastAPI(title="FastAPI", version="0.1.0")

# Мобилка-канон: пути без /api
app.include_router(router)

# Swagger/Web: те же пути, но с /api
app.include_router(router, prefix="/api")


# Static UI (если папка существует)
static_dir = os.path.join(os.path.dirname(__file__), "static")
if os.path.isdir(static_dir):
    app.mount("/static", StaticFiles(directory=static_dir), name="static")

    @app.get("/", response_class=HTMLResponse)
    async def index():
        index_path = os.path.join(static_dir, "index.html")
        if os.path.exists(index_path):
            with open(index_path, "r", encoding="utf-8") as f:
                return f.read()
        return "<html><body>static/index.html not found</body></html>"
else:
    @app.get("/", response_class=HTMLResponse)
    async def index():
        return "<html><body>Backend is running</body></html>"
