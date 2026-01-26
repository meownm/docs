import base64

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.settings import settings
from app import main as main_module


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
        return "req-1", {
            "parsed": {
                "document_number": "123456789",
                "date_of_birth": "1990-01-01",
                "date_of_expiry": "2030-01-01",
            },
            "input_payload": {"prompt": "demo"},
            "ollama_raw": {"message": {"content": "{}"}},
        }

    monkeypatch.setattr(main_module, "ollama_chat_with_image", fake_ollama)

    response = client.post(
        "/api/passport/recognize",
        files={"image": ("passport.jpg", b"fake-image", "image/jpeg")},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["request_id"] == "req-1"
    assert payload["mrz"] == {
        "document_number": "123456789",
        "date_of_birth": "1990-01-01",
        "date_of_expiry": "2030-01-01",
    }
    assert payload["error"] is None


def test_recognize_passport_empty_image_returns_422(client):
    response = client.post(
        "/api/passport/recognize",
        files={"image": ("passport.jpg", b"", "image/jpeg")},
    )

    assert response.status_code == 422


def test_recognize_passport_schema_error(client, monkeypatch):
    async def fake_ollama(image_bytes: bytes):
        return "req-2", {
            "parsed": {"document_number": "123456789"},
            "input_payload": {"prompt": "demo"},
            "ollama_raw": {"message": {"content": "{}"}},
        }

    monkeypatch.setattr(main_module, "ollama_chat_with_image", fake_ollama)

    response = client.post(
        "/api/passport/recognize",
        files={"image": ("passport.jpg", b"fake-image", "image/jpeg")},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["mrz"] is None
    assert payload["error"]["error_code"] == "SCHEMA_MISMATCH"


def test_store_nfc_invalid_base64_returns_422(client):
    response = client.post(
        "/api/passport/nfc",
        json={"passport": {"doc": "x"}, "face_image_b64": "not-base64"},
    )

    assert response.status_code == 422


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
