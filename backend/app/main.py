from fastapi import FastAPI

# ВАЖНО:
# Импорт через app.*, а не backend.*
# Это каноничный вариант для Windows + uvicorn + reload
from app.api import router

app = FastAPI(
    title="FastAPI",
    version="0.1.0",
)

# Composition root:
# Все роутеры подключаются ТОЛЬКО здесь
app.include_router(router, prefix="/api")
