import base64
import json

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


def test_recognize_passport_success(client, monkeypatch):
    async def fake_ollama(image_bytes: bytes):
        return "req-1", json.dumps(
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
    payload = response.json()
    assert payload == {
        "document_number": "123456789",
        "date_of_birth": "1990-01-01",
        "date_of_expiry": "2030-01-01",
    }


def test_recognize_passport_empty_image_returns_error_payload(client):
    response = client.post(
        "/api/passport/recognize",
        files={"image": ("passport.jpg", b"", "image/jpeg")},
    )

    assert response.status_code == 200
    assert response.json() == {"error": "Empty image file"}


def test_recognize_passport_schema_error(client, monkeypatch):
    async def fake_ollama(image_bytes: bytes):
        return "req-2", json.dumps({"document_number": "123456789"})

    monkeypatch.setattr(api_module, "ollama_chat_with_image", fake_ollama)

    response = client.post(
        "/api/passport/recognize",
        files={"image": ("passport.jpg", b"fake-image", "image/jpeg")},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload == {"error": "MRZ not found in recognition result"}


def test_store_nfc_invalid_base64_returns_error_payload(client):
    response = client.post(
        "/api/passport/nfc",
        json={"passport": {"doc": "x"}, "face_image_b64": "not-base64"},
    )

    assert response.status_code == 200
    assert response.json()["error"].startswith("Invalid face_image_b64:")


def test_store_nfc_and_fetch_face_integration(client):
    face_bytes = b"fake-image-bytes"
    response = client.post(
        "/api/passport/nfc",
        json={
            "passport": {"doc": "x"},
            "face_image_b64": base64.b64encode(face_bytes).decode("ascii"),
        },
    )

    assert response.status_code == 200
    scan_id = response.json()["scan_id"]

    face_response = client.get(f"/api/nfc/{scan_id}/face.jpg")
    assert face_response.status_code == 200
    assert face_response.content == face_bytes
