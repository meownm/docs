import base64
import json
import sqlite3

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.settings import Settings, settings
from app import api as api_module
from app import db as db_module
from app import settings as settings_module


@pytest.fixture()
def client(tmp_path, monkeypatch):
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
    monkeypatch.setattr(api_module, "settings", patched_settings)
    monkeypatch.setattr(db_module, "settings", patched_settings)
    with TestClient(app) as test_client:
        yield test_client


def test_recognize_mobile_success(client, monkeypatch):
    async def fake_ollama(image_bytes: bytes):
        return "req-1", json.dumps(
            {
                "document_number": "123456789",
                "date_of_birth": "19900101",
                "date_of_expiry": "20300101",
            }
        )

    monkeypatch.setattr(api_module, "ollama_chat_with_image", fake_ollama)

    response = client.post(
        "/recognize",
        files={"image": ("passport.jpg", b"fake-image", "image/jpeg")},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload == {
        "document_number": "123456789",
        "date_of_birth": "19900101",
        "date_of_expiry": "20300101",
    }


def test_recognize_passport_empty_image_returns_error_payload(client):
    response = client.post(
        "/recognize",
        files={"image": ("passport.jpg", b"", "image/jpeg")},
    )

    assert response.status_code == 200
    assert response.json() == {"error": "Empty image file"}


def test_recognize_mobile_mrz_not_found_error(client, monkeypatch):
    async def fake_ollama(image_bytes: bytes):
        return "req-2", json.dumps({"document_number": "123456789"})

    monkeypatch.setattr(api_module, "ollama_chat_with_image", fake_ollama)

    response = client.post(
        "/recognize",
        files={"image": ("passport.jpg", b"fake-image", "image/jpeg")},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload == {"error": "MRZ not found in recognition result"}


def test_store_nfc_invalid_base64_returns_error_payload(client):
    response = client.post(
        "/nfc",
        json={"passport": {"doc": "x"}, "face_image_b64": "not-base64"},
    )

    assert response.status_code == 422
    assert response.json()["detail"].startswith("Invalid face_image_b64:")


def test_store_nfc_missing_passport_returns_error_payload(client):
    response = client.post(
        "/nfc",
        json={"face_image_b64": base64.b64encode(b"face").decode("ascii")},
    )

    assert response.status_code == 422
    assert response.json() == {"detail": "Invalid passport"}


def test_store_nfc_and_fetch_face_integration(client):
    face_bytes = b"fake-image-bytes"
    response = client.post(
        "/nfc",
        json={
            "passport": {"doc": "x"},
            "face_image_b64": base64.b64encode(face_bytes).decode("ascii"),
        },
    )

    assert response.status_code == 200
    scan_id = response.json()["scan_id"]

    face_response = client.get(f"/nfc/{scan_id}/face.jpg")
    assert face_response.status_code == 200
    assert face_response.content == face_bytes


def _fetch_error_log(db_path: str, request_id: str):
    with sqlite3.connect(db_path) as conn:
        conn.row_factory = sqlite3.Row
        row = conn.execute(
            """
            SELECT *
            FROM app_error_logs
            WHERE request_id = ?
            ORDER BY id DESC
            LIMIT 1
            """,
            (request_id,),
        ).fetchone()
    return row


def test_store_app_error_log_mobile_integration(client):
    payload = {
        "platform": "ios",
        "app_version": "1.2.3",
        "error_message": "Crash in view",
        "stacktrace": "Traceback...",
        "context_json": {"screen": "home"},
        "user_agent": "MobileSafari",
        "device_info": "iPhone15,3",
        "request_id": "req-err-1",
    }

    response = client.post("/errors", json=payload)

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "stored"
    assert isinstance(body["id"], int)

    row = _fetch_error_log(settings_module.settings.db_path, "req-err-1")
    assert row is not None
    assert row["platform"] == "ios"
    assert row["error_message"] == "Crash in view"
    assert json.loads(row["context_json"]) == {"screen": "home"}


def test_store_app_error_log_swagger_integration(client):
    payload = {
        "platform": "web",
        "error_message": "ReferenceError: x is not defined",
        "request_id": "req-err-2",
    }

    response = client.post("/api/errors", json=payload)

    assert response.status_code == 200
    row = _fetch_error_log(settings_module.settings.db_path, "req-err-2")
    assert row is not None
    assert row["platform"] == "web"


def test_store_app_error_log_missing_required_fields(client):
    response = client.post("/errors", json={"error_message": "missing platform"})

    assert response.status_code == 422
