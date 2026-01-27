import json
import sqlite3

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.settings import settings
from app import api as api_module


@pytest.fixture()
def client(tmp_path, monkeypatch):
    data_dir = tmp_path / "data"
    files_dir = data_dir / "files"
    db_path = data_dir / "app.db"
    monkeypatch.setattr(settings, "data_dir", str(data_dir))
    monkeypatch.setattr(settings, "files_dir", str(files_dir))
    monkeypatch.setattr(settings, "db_path", str(db_path))
    with TestClient(app) as test_client:
        yield test_client


def _fetch_latest_log(db_path: str, path: str):
    with sqlite3.connect(db_path) as conn:
        conn.row_factory = sqlite3.Row
        row = conn.execute(
            """
            SELECT method, path, status_code, error
            FROM api_request_logs
            WHERE path = ?
            ORDER BY id DESC
            LIMIT 1
            """,
            (path,),
        ).fetchone()
    return row


def test_api_request_log_success(client, monkeypatch):
    async def fake_ollama(image_bytes: bytes):
        return "req-3", json.dumps(
            {
                "document_number": "123456789",
                "date_of_birth": "1990-01-01",
                "date_of_expiry": "2030-01-01",
            }
        )

    monkeypatch.setattr(api_module, "ollama_chat_with_image", fake_ollama)

    response = client.post(
        "/api/passport/recognize",
        files={"image": ("passport.jpg", b"fake-image", "image/jpeg")},
    )

    assert response.status_code == 200

    row = _fetch_latest_log(settings.db_path, "/api/passport/recognize")
    assert row is not None
    assert row["method"] == "POST"
    assert row["status_code"] == 200
    assert row["error"] is None


def test_api_request_log_error(client):
    response = client.get("/api/does-not-exist")

    assert response.status_code == 404

    row = _fetch_latest_log(settings.db_path, "/api/does-not-exist")
    assert row is not None
    assert row["method"] == "GET"
    assert row["status_code"] == 404
