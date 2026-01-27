import os
import aiosqlite
from .settings import settings


DDL = [
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
    """
    CREATE TABLE IF NOT EXISTS nfc_scans (
        scan_id TEXT PRIMARY KEY,
        ts_utc TEXT NOT NULL,
        passport_json TEXT NOT NULL,
        face_image_path TEXT NOT NULL
    );
    """,
]


async def init_db() -> None:
    os.makedirs(os.path.dirname(settings.db_path), exist_ok=True)
    async with aiosqlite.connect(settings.db_path) as db:
        for stmt in DDL:
            await db.execute(stmt)
        await db.commit()


async def get_db():
    db = await aiosqlite.connect(settings.db_path)
    try:
        yield db
    finally:
        await db.close()
