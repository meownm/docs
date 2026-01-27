import base64
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
            SELECT method, path, status_code, error, request_body, response_body
            FROM api_request_logs
            WHERE path = ?
            ORDER BY id DESC
            LIMIT 1
            """,
            (path,),
        ).fetchone()
    return row


def _parse_placeholder(body: str | None) -> dict:
    assert body is not None
    payload = json.loads(body)
    assert payload["placeholder"] == "binary_or_too_large"
    assert payload["size"] > 0
    return payload


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
    _parse_placeholder(row["request_body"])
    response_payload = json.loads(row["response_body"])
    assert response_payload["document_number"] == "123456789"


def test_api_request_log_error(client):
    response = client.get("/api/does-not-exist")

    assert response.status_code == 404

    row = _fetch_latest_log(settings.db_path, "/api/does-not-exist")
    assert row is not None
    assert row["method"] == "GET"
    assert row["status_code"] == 404
    response_payload = json.loads(row["response_body"])
    assert response_payload["detail"] == "Not Found"


def test_api_request_log_json_body_and_response(client):
    payload = {
        "passport": {
            "document_number": "123456789",
            "date_of_birth": "1990-01-01",
            "date_of_expiry": "2030-01-01",
        },
        "face_image_b64": base64.b64encode(b"tiny-face").decode("utf-8"),
    }

    response = client.post("/api/passport/nfc", json=payload)

    assert response.status_code == 200

    row = _fetch_latest_log(settings.db_path, "/api/passport/nfc")
    assert row is not None
    request_payload = json.loads(row["request_body"])
    assert request_payload["passport"]["document_number"] == "123456789"
    response_payload = json.loads(row["response_body"])
    assert response_payload["scan_id"] == response.json()["scan_id"]


def test_api_request_log_large_body_placeholder(client):
    large_bytes = b"a" * 70000
    payload = {
        "passport": {
            "document_number": "987654321",
            "date_of_birth": "1991-01-01",
            "date_of_expiry": "2031-01-01",
        },
        "face_image_b64": base64.b64encode(large_bytes).decode("utf-8"),
    }

    response = client.post("/api/passport/nfc", json=payload)

    assert response.status_code == 200

    row = _fetch_latest_log(settings.db_path, "/api/passport/nfc")
    assert row is not None
    _parse_placeholder(row["request_body"])


def test_api_request_log_binary_response_placeholder(client):
    payload = {
        "passport": {
            "document_number": "111111111",
            "date_of_birth": "1992-01-01",
            "date_of_expiry": "2032-01-01",
        },
        "face_image_b64": base64.b64encode(b"binary-face").decode("utf-8"),
    }

    response = client.post("/api/passport/nfc", json=payload)
    assert response.status_code == 200
    scan_id = response.json()["scan_id"]

    image_response = client.get(f"/api/nfc/{scan_id}/face.jpg")
    assert image_response.status_code == 200

    row = _fetch_latest_log(settings.db_path, f"/api/nfc/{scan_id}/face.jpg")
    assert row is not None
    _parse_placeholder(row["response_body"])
