import asyncio
import sqlite3

import httpx
import pytest

from app import db as db_module
from app import llm as llm_module
from app import settings as settings_module
from app.settings import Settings, settings


class FakeAsyncClient:
    def __init__(self, response: httpx.Response):
        self._response = response

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, tb):
        return False

    async def post(self, url: str, json: dict):
        return self._response


@pytest.fixture()
def llm_db(tmp_path, monkeypatch):
    data_dir = tmp_path / "data"
    files_dir = data_dir / "files"
    db_path = data_dir / "app.db"
    data_dir.mkdir(parents=True, exist_ok=True)
    files_dir.mkdir(parents=True, exist_ok=True)
    patched_settings = Settings(
        files_dir=str(files_dir),
        db_path=str(db_path),
        host=settings.host,
        port=settings.port,
        ollama_base_url=settings.ollama_base_url,
        ollama_model=settings.ollama_model,
        ollama_timeout_sec=settings.ollama_timeout_sec,
    )
    monkeypatch.setattr(settings_module, "settings", patched_settings)
    monkeypatch.setattr(llm_module, "settings", patched_settings)
    monkeypatch.setattr(db_module, "settings", patched_settings)
    asyncio.run(db_module.init_db())
    return str(db_path)


def fetch_rows(db_path: str, query: str, params: tuple = ()) -> list[sqlite3.Row]:
    with sqlite3.connect(db_path) as conn:
        conn.row_factory = sqlite3.Row
        return conn.execute(query, params).fetchall()


def test_ollama_logging_success(llm_db, monkeypatch):
    response = httpx.Response(
        200,
        request=httpx.Request("POST", "http://ollama/api/chat"),
        json={"message": {"content": "ok"}},
    )
    monkeypatch.setattr(
        llm_module.httpx,
        "AsyncClient",
        lambda timeout: FakeAsyncClient(response),
    )

    request_id, content = asyncio.run(llm_module.ollama_chat_with_image(b"image"))

    assert content == "ok"
    rows = fetch_rows(llm_db, "SELECT * FROM llm_logs WHERE request_id = ?", (request_id,))
    assert len(rows) == 1
    row = rows[0]
    assert row["success"] == 1
    assert row["error"] is None
    assert '"message"' in row["output_json"]


def test_ollama_logging_http_error(llm_db, monkeypatch):
    response = httpx.Response(
        500,
        request=httpx.Request("POST", "http://ollama/api/chat"),
        text="boom",
    )
    monkeypatch.setattr(
        llm_module.httpx,
        "AsyncClient",
        lambda timeout: FakeAsyncClient(response),
    )

    with pytest.raises(RuntimeError):
        asyncio.run(llm_module.ollama_chat_with_image(b"image"))

    rows = fetch_rows(llm_db, "SELECT * FROM llm_logs")
    assert len(rows) == 1
    row = rows[0]
    assert row["success"] == 0
    assert "Ollama HTTP error" in row["error"]
