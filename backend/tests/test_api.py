import base64
import json
import asyncio
import sqlite3

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.settings import Settings, settings
from app import api as api_module
from app import db as db_module
from app import settings as settings_module
from app.events import event_bus

JPEG_BYTES = b"\xff\xd8\xff" + (b"jpeg-bytes" * 15) + b"\xff\xd9"

# Simulated DG2 structure with embedded JPEG (header + JPEG + trailer)
DG2_WITH_JPEG = b"\x75\x82\x00\x50" + b"\x7f\x61" + JPEG_BYTES + b"\x00\x00"

# Simulated DG2 structure with embedded JP2 codestream
JP2_CODESTREAM_BYTES = b"\xff\x4f\xff\x51" + b"jp2-codestream-data"
DG2_WITH_JP2_CODESTREAM = b"\x75\x82\x00\x50" + b"\x7f\x61" + JP2_CODESTREAM_BYTES

def fetch_rows(db_path: str, query: str, params: tuple = ()) -> list[sqlite3.Row]:
    with sqlite3.connect(db_path) as conn:
        conn.row_factory = sqlite3.Row
        return conn.execute(query, params).fetchall()


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
        "date_of_birth": "900101",
        "date_of_expiry": "300101",
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
    rows = fetch_rows(settings_module.settings.db_path, "SELECT * FROM nfc_scans")
    assert rows == []


def test_store_nfc_rejects_unconvertible_jp2(client):
    jp2_bytes = b"\x00\x00\x00\x0cjP  \r\n\x87\n" + b"not-real-jp2"
    response = client.post(
        "/nfc",
        json={
            "passport": {"doc": "x"},
            "face_image_b64": base64.b64encode(jp2_bytes).decode("ascii"),
        },
    )

    assert response.status_code == 422
    assert response.json() == {
        "detail": "Invalid face_image_b64: expected JPEG or JP2 convertible to JPEG",
    }


def test_store_nfc_missing_passport_returns_error_payload(client):
    response = client.post(
        "/nfc",
        json={"face_image_b64": base64.b64encode(JPEG_BYTES).decode("ascii")},
    )

    assert response.status_code == 422
    assert response.json() == {"detail": "Invalid passport"}


def test_store_nfc_normalizes_nested_mrz_dates(client):
    response = client.post(
        "/nfc",
        json={
            "passport": {
                "mrz": {
                    "document_number": "AA1234567",
                    "date_of_birth": "1990-01-01",
                    "date_of_expiry": "20300101",
                }
            },
            "face_image_b64": base64.b64encode(JPEG_BYTES).decode("ascii"),
        },
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["passport"]["mrz"]["date_of_birth"] == "900101"
    assert payload["passport"]["mrz"]["date_of_expiry"] == "300101"


def test_store_nfc_and_fetch_face_integration(client):
    face_bytes = JPEG_BYTES
    passport_payload = {
        "doc": "x",
        "document_number": "123456789",
        "date_of_birth": "19900101",
        "date_of_expiry": "2030-01-01",
    }
    published_events = []

    async def fake_publish(event):
        published_events.append(event)

    with pytest.MonkeyPatch.context() as mp:
        mp.setattr(event_bus, "publish", fake_publish)
        response = client.post(
        "/nfc",
        json={
            "passport": passport_payload,
            "face_image_b64": base64.b64encode(face_bytes).decode("ascii"),
        },
    )

    assert response.status_code == 200
    response_payload = response.json()
    scan_id = response_payload["scan_id"]
    assert response_payload["face_image_url"] == f"/api/nfc/{scan_id}/face.jpg"
    assert response_payload["passport"]["doc"] == "x"
    assert response_payload["passport"]["date_of_birth"] == "900101"
    assert response_payload["passport"]["date_of_expiry"] == "300101"

    face_response = client.get(f"/nfc/{scan_id}/face.jpg")
    assert face_response.status_code == 200
    assert face_response.content == face_bytes
    rows = fetch_rows(
        settings_module.settings.db_path,
        "SELECT * FROM nfc_scans WHERE scan_id = ?",
        (scan_id,),
    )
    assert len(rows) == 1
    row = rows[0]
    assert row["face_image_path"].endswith(f"{scan_id}_face.jpg")
    stored_passport = json.loads(row["passport_json"])
    assert stored_passport["doc"] == "x"
    assert stored_passport["document_number"] == "123456789"
    assert stored_passport["date_of_birth"] == "900101"
    assert stored_passport["date_of_expiry"] == "300101"
    assert published_events == [
        {
            "type": "nfc_scan_success",
            "scan_id": scan_id,
            "face_image_url": f"/api/nfc/{scan_id}/face.jpg",
            "passport": stored_passport,
        }
    ]


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


def test_store_app_error_log_missing_platform_returns_422(client):
    response = client.post(
        "/errors",
        json={"error_message": "Missing platform"},
    )

    assert response.status_code == 422
    detail = response.json()["detail"]
    assert any(item["loc"][-1] == "platform" for item in detail)


def test_store_app_error_log_missing_error_message_returns_422(client):
    response = client.post(
        "/api/errors",
        json={"platform": "web"},
    )

    assert response.status_code == 422
    detail = response.json()["detail"]
    assert any(item["loc"][-1] == "error_message" for item in detail)


def test_store_app_error_log_missing_required_fields(client):
    response = client.post("/errors", json={"error_message": "missing platform"})

    assert response.status_code == 422
@pytest.mark.anyio
async def test_sse_event_format_and_payload(patched_settings):
    event_payload = {
        "type": "nfc_scan_success",
        "scan_id": "scan-1",
        "face_image_url": "/api/nfc/scan-1/face.jpg",
        "passport": {"doc": "x"},
    }

    stream = api_module._event_stream()

    async def publish_after_delay():
        await asyncio.sleep(0.01)
        await event_bus.publish(event_payload)

    asyncio.create_task(publish_after_delay())
    message = await stream.__anext__()

    lines = [line for line in message.splitlines() if line]
    assert lines[0] == "event: nfc_scan_success"
    assert lines[1].startswith("data: ")
    payload = json.loads(lines[1].removeprefix("data: ").strip())
    assert payload == event_payload


@pytest.mark.anyio
async def test_sse_event_missing_type_defaults_message(patched_settings):
    event_payload = {"scan_id": "scan-2"}

    stream = api_module._event_stream()

    async def publish_after_delay():
        await asyncio.sleep(0.01)
        await event_bus.publish(event_payload)

    asyncio.create_task(publish_after_delay())
    message = await stream.__anext__()

    lines = [line for line in message.splitlines() if line]
    assert lines[0] == "event: message"
    payload = json.loads(lines[1].removeprefix("data: ").strip())
    assert payload == event_payload


def test_store_nfc_raw_format_with_mrz_keys_and_dg2(client):
    """Test raw NFC format with mrz_keys and dg2_raw_b64 fields."""
    response = client.post(
        "/nfc",
        json={
            "mrz_keys": {
                "document_number": "764507757",
                "date_of_birth": "810809",
                "date_of_expiry": "310503",
            },
            "dg2_raw_b64": base64.b64encode(DG2_WITH_JPEG).decode("ascii"),
            "dg1_raw_b64": base64.b64encode(b"dg1-data").decode("ascii"),
            "format": "raw",
        },
    )

    assert response.status_code == 200
    payload = response.json()
    assert "scan_id" in payload
    assert payload["passport"]["mrz"]["document_number"] == "764507757"
    assert payload["passport"]["mrz"]["date_of_birth"] == "810809"
    assert payload["passport"]["mrz"]["date_of_expiry"] == "310503"


def test_store_nfc_raw_format_extracts_face_from_dg2(client):
    """Test that face image is correctly extracted from DG2 and can be fetched."""
    response = client.post(
        "/nfc",
        json={
            "mrz_keys": {
                "document_number": "123456789",
                "date_of_birth": "900101",
                "date_of_expiry": "300101",
            },
            "dg2_raw_b64": base64.b64encode(DG2_WITH_JPEG).decode("ascii"),
        },
    )

    assert response.status_code == 200
    scan_id = response.json()["scan_id"]

    face_response = client.get(f"/nfc/{scan_id}/face.jpg")
    assert face_response.status_code == 200
    assert face_response.content == JPEG_BYTES


def test_store_nfc_raw_format_invalid_dg2_base64(client):
    """Test error handling for invalid base64 in dg2_raw_b64."""
    response = client.post(
        "/nfc",
        json={
            "mrz_keys": {
                "document_number": "123456789",
                "date_of_birth": "900101",
                "date_of_expiry": "300101",
            },
            "dg2_raw_b64": "not-valid-base64!!!",
        },
    )

    assert response.status_code == 422
    assert "Invalid dg2_raw_b64" in response.json()["detail"]


def test_store_nfc_raw_format_dg2_without_face(client):
    """Test error handling when DG2 doesn't contain extractable face image."""
    response = client.post(
        "/nfc",
        json={
            "mrz_keys": {
                "document_number": "123456789",
                "date_of_birth": "900101",
                "date_of_expiry": "300101",
            },
            "dg2_raw_b64": base64.b64encode(b"no-image-here").decode("ascii"),
        },
    )

    assert response.status_code == 422
    assert response.json() == {
        "detail": "Invalid dg2_raw_b64: could not extract face image from DG2"
    }


def test_store_nfc_prefers_face_image_b64_over_dg2(client):
    """Test that face_image_b64 is used when both fields are provided."""
    different_jpeg = b"\xff\xd8\xff" + (b"different" * 15) + b"\xff\xd9"
    response = client.post(
        "/nfc",
        json={
            "passport": {"document_number": "123", "date_of_birth": "900101", "date_of_expiry": "300101"},
            "face_image_b64": base64.b64encode(different_jpeg).decode("ascii"),
            "dg2_raw_b64": base64.b64encode(DG2_WITH_JPEG).decode("ascii"),
        },
    )

    assert response.status_code == 200
    scan_id = response.json()["scan_id"]

    face_response = client.get(f"/nfc/{scan_id}/face.jpg")
    assert face_response.content == different_jpeg
