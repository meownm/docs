import base64
import json
import asyncio

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.settings import Settings, settings
from app import api as api_module
from app import db as db_module
from app import settings as settings_module
from app.events import event_bus


@pytest.fixture()
def patched_settings(tmp_path, monkeypatch):
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
    return patched_settings


@pytest.fixture()
def client(patched_settings):
    with TestClient(app) as test_client:
        yield test_client


def test_recognize_mobile_success(client, monkeypatch):
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
        "/recognize",
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

    assert response.status_code == 200
    assert response.json()["error"].startswith("Invalid face_image_b64:")


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


async def _drain_event_bus():
    while True:
        try:
            event_bus._queue.get_nowait()
        except asyncio.QueueEmpty:
            break


@pytest.mark.anyio
async def test_sse_event_format_and_payload(patched_settings):
    await _drain_event_bus()
    event_payload = {
        "type": "nfc_scan_success",
        "scan_id": "scan-1",
        "face_image_url": "/api/nfc/scan-1/face.jpg",
        "passport": {"doc": "x"},
    }

    stream = api_module._event_stream()
    await event_bus.publish(event_payload)
    message = await stream.__anext__()

    lines = [line for line in message.splitlines() if line]
    assert lines[0] == "event: nfc_scan_success"
    assert lines[1].startswith("data: ")
    payload = json.loads(lines[1].removeprefix("data: ").strip())
    assert payload == event_payload


@pytest.mark.anyio
async def test_sse_event_missing_type_defaults_message(patched_settings):
    await _drain_event_bus()
    event_payload = {"scan_id": "scan-2"}

    stream = api_module._event_stream()
    await event_bus.publish(event_payload)
    message = await stream.__anext__()

    lines = [line for line in message.splitlines() if line]
    assert lines[0] == "event: message"
    payload = json.loads(lines[1].removeprefix("data: ").strip())
    assert payload == event_payload
