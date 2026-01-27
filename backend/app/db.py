import os
from contextlib import asynccontextmanager

import aiosqlite
from .settings import settings


DDL = [
    # -------------------------
    # LLM logs
    # -------------------------
    """
    CREATE TABLE IF NOT EXISTS llm_logs (
        id TEXT PRIMARY KEY,
        ts_utc TEXT NOT NULL,
        request_id TEXT NOT NULL,
        model TEXT NOT NULL,
        input_json TEXT NOT NULL,
        output_json TEXT,
        success INTEGER NOT NULL,
        error TEXT
    );
    """,

    # -------------------------
    # NFC scans
    # -------------------------
    """
    CREATE TABLE IF NOT EXISTS nfc_scans (
        scan_id TEXT PRIMARY KEY,
        ts_utc TEXT NOT NULL,
        passport_json TEXT NOT NULL,
        face_image_path TEXT NOT NULL
    );
    """,

    # -------------------------
    # HTTP API request logs
    # -------------------------
    """
    CREATE TABLE IF NOT EXISTS api_request_logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        ts_utc TEXT NOT NULL,
        method TEXT NOT NULL,
        path TEXT NOT NULL,
        query TEXT,
        status_code INTEGER,
        duration_ms INTEGER,
        client_ip TEXT,
        user_agent TEXT,
        content_type TEXT,
        content_length INTEGER,
        error TEXT
    );
    """,

    # -------------------------
    # App error logs
    # -------------------------
    """
    CREATE TABLE IF NOT EXISTS app_error_logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        ts_utc TEXT NOT NULL,
        platform TEXT NOT NULL,
        app_version TEXT,
        error_message TEXT NOT NULL,
        stacktrace TEXT,
        context_json TEXT,
        user_agent TEXT,
        device_info TEXT,
        request_id TEXT
    );
    """,
]


async def init_db() -> None:
    """
    Инициализация БД при старте приложения.
    Создаёт все таблицы, если их ещё нет.
    """
    os.makedirs(os.path.dirname(settings.db_path), exist_ok=True)

    async with aiosqlite.connect(settings.db_path) as db:
        for stmt in DDL:
            await db.execute(stmt)
        await db.commit()


@asynccontextmanager
async def get_db():
    """
    Асинхронный контекстный менеджер соединений с БД.
    Используется в middleware и сервисах.
    """
    db = await aiosqlite.connect(settings.db_path)
    try:
        yield db
    finally:
        await db.close()
