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


def _fetch_logs(db_path: str, path: str) -> list[sqlite3.Row]:
    with sqlite3.connect(db_path) as conn:
        conn.row_factory = sqlite3.Row
        return conn.execute(
            "SELECT * FROM api_request_logs WHERE path = ? ORDER BY id DESC",
            (path,),
        ).fetchall()


def test_recognize_date_normalization_iso(client, monkeypatch):
    async def fake_ollama(image_bytes: bytes):
        return "req-1", json.dumps(
            {
                "document_number": "AB123",
                "date_of_birth": "1990-01-01",
                "date_of_expiry": "2030-12-31",
            }
        )

    monkeypatch.setattr(api_module, "ollama_chat_with_image", fake_ollama)

    response = client.post(
        "/recognize",
        files={"image": ("passport.jpg", b"fake-image", "image/jpeg")},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["date_of_birth"].isdigit()
    assert len(payload["date_of_birth"]) == 6
    assert payload["date_of_expiry"].isdigit()
    assert len(payload["date_of_expiry"]) == 6


def test_recognize_date_normalization_yyyymmdd(client, monkeypatch):
    async def fake_ollama(image_bytes: bytes):
        return "req-2", json.dumps(
            {
                "document_number": "AB123",
                "date_of_birth": "19900101",
                "date_of_expiry": "20301231",
            }
        )

    monkeypatch.setattr(api_module, "ollama_chat_with_image", fake_ollama)

    response = client.post(
        "/recognize",
        files={"image": ("passport.jpg", b"fake-image", "image/jpeg")},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["date_of_birth"] == "900101"
    assert payload["date_of_expiry"] == "301231"


@pytest.mark.parametrize(
    "body",
    [
        {"mrz": {"document_number": "AB123", "date_of_birth": "1990-01-01", "date_of_expiry": "2030-01-01"}},
        {"document_number": "AB123", "date_of_birth": "1990-01-01", "date_of_expiry": "2030-01-01"},
        {"fields": {"passport_number": "AB123", "date_of_birth": "1990-01-01", "date_of_expiry": "2030-01-01"}},
    ],
)
def test_recognize_parser_variants(client, monkeypatch, body):
    async def fake_ollama(image_bytes: bytes):
        return "req-3", json.dumps(body)

    monkeypatch.setattr(api_module, "ollama_chat_with_image", fake_ollama)

    response = client.post(
        "/recognize",
        files={"image": ("passport.jpg", b"fake-image", "image/jpeg")},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["document_number"]
    assert payload["date_of_birth"].isdigit()
    assert len(payload["date_of_birth"]) == 6
    assert payload["date_of_expiry"].isdigit()
    assert len(payload["date_of_expiry"]) == 6


@pytest.mark.parametrize(
    "payload",
    [
        {
            "passport": {
                "mrz": {
                    "document_number": "ab123",
                    "date_of_birth": "1990-01-01",
                    "date_of_expiry": "2030-01-01",
                }
            },
            "face_image_b64": base64.b64encode(b"face-bytes" * 20).decode("ascii"),
        },
        {
            "mrz": {
                "document_number": "ab123",
                "date_of_birth": "1990-01-01",
                "date_of_expiry": "2030-01-01",
            },
            "face_image_b64": base64.b64encode(b"face-bytes" * 20).decode("ascii"),
        },
        {
            "passport": {
                "document_number": "ab123",
                "date_of_birth": "1990-01-01",
                "date_of_expiry": "2030-01-01",
            },
            "face_image_b64": base64.b64encode(b"face-bytes" * 20).decode("ascii"),
        },
    ],
)
def test_nfc_request_backward_compat(client, payload):
    response = client.post("/nfc", json=payload)

    assert response.status_code == 200
    data = response.json()
    assert data["scan_id"]
    assert data["face_image_url"].startswith("/api/nfc/")
    assert data["face_image_url"].endswith("/face.jpg")
    assert data["passport"]["mrz"]["date_of_birth"] == "900101"
    assert data["passport"]["mrz"]["date_of_expiry"] == "300101"
    assert data["passport"]["mrz"]["document_number"] == "AB123"


def test_nfc_face_jpg_endpoint(client):
    payload = {
        "passport": {
            "mrz": {
                "document_number": "AB123",
                "date_of_birth": "1990-01-01",
                "date_of_expiry": "2030-01-01",
            }
        },
        "face_image_b64": base64.b64encode(b"face-bytes" * 20).decode("ascii"),
    }
    response = client.post("/nfc", json=payload)
    assert response.status_code == 200
    scan_id = response.json()["scan_id"]

    face_response = client.get(f"/api/nfc/{scan_id}/face.jpg")
    assert face_response.status_code == 200
    assert face_response.headers["content-type"].startswith("image/jpeg")
    assert len(face_response.content) > 100


def test_sse_payload_contains_face_image_url(client, monkeypatch):
    events = []

    async def fake_publish(event):
        events.append(event)

    monkeypatch.setattr(event_bus, "publish", fake_publish)

    payload = {
        "passport": {
            "mrz": {
                "document_number": "AB123",
                "date_of_birth": "1990-01-01",
                "date_of_expiry": "2030-01-01",
            }
        },
        "face_image_b64": base64.b64encode(b"face-bytes" * 20).decode("ascii"),
    }
    response = client.post("/nfc", json=payload)
    assert response.status_code == 200
    scan_id = response.json()["scan_id"]

    assert events == [
        {
            "type": "nfc_scan_success",
            "scan_id": scan_id,
            "face_image_url": f"/api/nfc/{scan_id}/face.jpg",
            "passport": response.json()["passport"],
        }
    ]


def test_api_logging_json_bodies(client, patched_settings, monkeypatch):
    async def fake_ollama(image_bytes: bytes):
        return "req-1", json.dumps(
            {
                "document_number": "AB123",
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

    nfc_response = client.post(
        "/nfc",
        json={
            "passport": {
                "mrz": {
                    "document_number": "AB123",
                    "date_of_birth": "1990-01-01",
                    "date_of_expiry": "2030-01-01",
                }
            },
            "face_image_b64": base64.b64encode(b"face-bytes" * 20).decode("ascii"),
        },
    )
    assert nfc_response.status_code == 200
    scan_id = nfc_response.json()["scan_id"]

    face_response = client.get(f"/api/nfc/{scan_id}/face.jpg")
    assert face_response.status_code == 200

    recognize_logs = _fetch_logs(patched_settings.db_path, "/recognize")
    nfc_logs = _fetch_logs(patched_settings.db_path, "/nfc")
    face_logs = _fetch_logs(patched_settings.db_path, f"/api/nfc/{scan_id}/face.jpg")

    assert recognize_logs[0]["response_body"] != ""
    assert "placeholder" not in recognize_logs[0]["response_body"]
    assert nfc_logs[0]["response_body"] != ""
    assert "placeholder" not in nfc_logs[0]["response_body"]
    assert face_logs[0]["response_body"] != ""
