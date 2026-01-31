# CLAUDE.md

## Project Overview

Demo project for passport photo recognition + NFC (eMRTD) verification flow. **This is a demonstration project not intended for further development.**

The system consists of three components:
1. **Backend** (Python/FastAPI) - Receives passport photos, recognizes via multimodal LLM (Ollama + qwen3-vl:30b), handles NFC scan results, stores data, and serves web UI
2. **Web UI** - Built into backend, displays status and face photo after successful NFC scan via SSE
3. **Mobile Android (Java)** - Camera preview, sends photo to backend, displays recognized parameters, reads NFC (eMRTD) chip, sends data to backend

## Repository Structure

```
/
├── backend/                    # Python FastAPI backend (single source of truth)
│   ├── app/                    # Application code
│   │   ├── main.py             # FastAPI app entry, lifespan, router mounting
│   │   ├── api.py              # API endpoints (/recognize, /nfc, /events, /errors)
│   │   ├── api_logging.py      # HTTP request/response logging middleware
│   │   ├── db.py               # SQLite database initialization and connection
│   │   ├── events.py           # SSE event bus implementation
│   │   ├── llm.py              # Ollama LLM integration with logging
│   │   ├── llm_service.py      # Stateless LLM service wrapper
│   │   ├── logging_setup.py    # Structured logging configuration
│   │   ├── schemas.py          # Pydantic models
│   │   ├── settings.py         # Environment configuration
│   │   └── static/             # Web UI (index.html, style.css)
│   ├── tests/                  # pytest tests
│   ├── pyproject.toml          # Poetry dependencies
│   ├── .env.example            # Environment template
│   ├── install.bat             # Windows: poetry install
│   └── run_dev.bat             # Windows: uvicorn start
├── mobile_android_java/        # Android Java mobile app
│   ├── app/src/main/java/com/demo/passport/
│   │   ├── MainActivity.java       # Main activity, state machine, UI
│   │   ├── BackendApi.java         # HTTP client for backend
│   │   ├── BackendConfig.java      # Backend URL configuration
│   │   ├── NfcPassportReader.java  # eMRTD NFC reading (JMRTD)
│   │   ├── NfcPayloadBuilder.java  # JSON payload construction
│   │   ├── NfcReadResult.java      # NFC read result model
│   │   ├── NfcReadStatus.java      # NFC status enum
│   │   ├── NfcDispatchTransition.java # NFC dispatch state
│   │   ├── UiStateModel.java       # UI state machine model
│   │   └── PhotoCaptureUtils.java  # Photo capture utilities
│   ├── app/src/test/           # JUnit unit tests
│   ├── app/src/androidTest/    # Instrumentation tests
│   └── build.gradle            # Gradle dependencies
├── web/                        # Web UI placeholder (actual UI in backend/app/static)
├── docs/                       # Documentation
│   ├── contracts/nfc.md        # NFC API contract specification
│   ├── requirements_registry.md # Requirements tracking
│   ├── decision_log.md         # Architecture decisions log
│   └── regression_checklist.md # Manual/automated test checklist
└── README.md                   # Project overview and quick start
```

## Technology Stack

### Backend
- **Python 3.11+** with Poetry for dependency management
- **FastAPI** (0.115+) with uvicorn
- **SQLite** (aiosqlite) for data storage
- **Ollama** with qwen3-vl:30b model for passport recognition
- **Pillow** for image processing (JP2 to JPEG conversion)
- **Ruff** for linting (line-length: 100)

### Mobile (Android)
- **Java 11** (compileSdk 34, minSdk 26, targetSdk 34)
- **JMRTD 0.7.18** for eMRTD/NFC passport reading
- **OkHttp3** for HTTP requests
- **Gson** for JSON parsing
- **Bouncy Castle** for cryptography
- **CameraX** for camera preview

### Database Tables (SQLite)
- `llm_logs` - LLM input/output logging
- `nfc_scans` - NFC scan results with face images
- `api_request_logs` - HTTP request/response logging
- `app_error_logs` - Application error reports

## API Contracts

### Canonical Paths (for mobile)
Use paths **without** `/api` prefix. The `/api` prefix is for Swagger/Web only.

### POST `/recognize`
Upload passport photo for MRZ recognition.
- Input: `multipart/form-data` with `image` field
- Success (200): `{ "document_number": "...", "date_of_birth": "YYMMDD", "date_of_expiry": "YYMMDD" }`
- Error (200): `{ "error": "..." }`
- **Dates always returned in YYMMDD format**

### POST `/nfc`
Submit NFC scan results.
- Input: JSON with `passport` object and `face_image_b64` (base64 JPEG)
- Also accepts: `dg2_raw_b64` (raw DG2 bytes), `mrz`/`mrz_keys` for MRZ data
- Success (200): `{ "scan_id": "uuid", "face_image_url": "/api/nfc/<scan_id>/face.jpg", "passport": {...} }`
- Error (422): `{ "detail": "..." }`
- Dates normalized: accepts YYMMDD, YYYYMMDD, YYYY-MM-DD → returns YYMMDD
- `document_number` normalized: trim + remove whitespace + uppercase

### GET `/nfc/{scan_id}/face.jpg`
Returns stored face image (JPEG).

### GET `/events`
SSE stream. Emits `nfc_scan_success` event after successful NFC upload.

### POST `/errors`
Application error logging endpoint.
- Required: `platform` (ios/android/web), `error_message`
- Optional: `ts_utc`, `app_version`, `stacktrace`, `context_json`, `user_agent`, `device_info`, `request_id`

## Development Workflow

### Backend

```bash
cd backend

# Install dependencies
poetry install

# Run development server
poetry run uvicorn app.main:app --host 0.0.0.0 --port 30450 --reload

# Run tests
poetry run pytest

# Windows shortcuts
install.bat   # poetry install
run_dev.bat   # start uvicorn
```

### Mobile (Android)

```bash
cd mobile_android_java

# Run unit tests
./gradlew test

# Run instrumentation tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Build debug APK
./gradlew assembleDebug
```

### Configuration

Backend environment variables (see `.env.example`):
- `BACKEND_HOST`/`BACKEND_PORT` (fallback: `APP_HOST`/`APP_PORT`)
- `OLLAMA_BASE_URL`, `OLLAMA_MODEL`, `OLLAMA_TIMEOUT_SECONDS`
- `DATA_DIR`, `DB_PATH`, `FILES_DIR`

Android: Set backend URL in `BackendConfig.java`.

## Code Conventions

### Backend (Python)
- Use `from __future__ import annotations` for type hints
- Ruff linting with line-length 100
- Async/await for all I/O operations
- `request_id` required in structured logs (placeholder `n/a` if missing)
- `LLMService` is stateless - `request_id` passed to methods, not stored

### Mobile (Android Java)
- State machine pattern: `CAMERA → PHOTO_SENDING → NFC_WAIT → NFC_READING → RESULT/ERROR`
- NFC foreground dispatch enabled only in `NFC_WAIT` state
- FileProvider authority: `${applicationId}.fileprovider`
- MRZ validation before NFC start (YYMMDD format check)
- Background thread for NFC reading (UI not blocked)
- Raw API responses shown in debug section

### API Compatibility
- Backend accepts multiple MRZ formats (legacy support)
- Date normalization always outputs YYMMDD
- Face images: JPEG preferred, JP2 accepted (auto-converted)
- JPEG with small trailing bytes (≤64) after EOI accepted

## Testing

### Backend Tests (`backend/tests/`)
- `test_api.py` - API endpoint tests
- `test_contracts_regression.py` - Contract compatibility tests
- `test_api_logging.py` - Request/response logging tests
- `test_llm_logging.py` - LLM logging tests
- `test_settings_env.py` - Environment variable tests

### Mobile Tests
- Unit tests: `app/src/test/java/` (NFC, MRZ validation, date normalization)
- Instrumentation: `app/src/androidTest/java/` (FileProvider, manual MRZ entry)

### Key Test Files
- `DateNormalizeTest.java` - Date format normalization
- `NfcPayloadBuilderTest.java` - Payload construction
- `test_contracts_regression.py` - API contract regression

## Important Architecture Decisions

1. **Web UI embedded in backend** - Minimizes deployment complexity for demo scope
2. **SSE for real-time updates** - Simpler than WebSocket for demo purposes
3. **LLM via Ollama `/api/chat`** with `format=json` - Simplifies response parsing
4. **Face photos stored on disk** (not SQLite BLOB) - Simpler for demo
5. **LLMService is stateless** - Prevents state leakage between requests
6. **NFC BAC requires `sendSelectApplet(false)`** before `doBAC()` - Chip compatibility

## File Storage

- Database: `backend/data/app.db`
- Face images: `backend/data/files/{scan_id}_face.jpg`
- These directories are gitignored

## Common Tasks

### Adding a new API endpoint
1. Add route handler in `backend/app/api.py`
2. Add tests in `backend/tests/`
3. Update `docs/contracts/` if needed
4. Update regression checklist if needed

### Modifying NFC flow (Android)
1. Edit `NfcPassportReader.java` for reading logic
2. Update `NfcPayloadBuilder.java` for payload format
3. Update state machine in `MainActivity.java` if needed
4. Add/update tests in `app/src/test/`

### Testing LLM integration
- Mock Ollama responses in tests
- Check `llm_logs` table for debugging
- Ensure `request_id` correlation in logs

## Documentation

- `docs/contracts/nfc.md` - Authoritative NFC API specification
- `docs/requirements_registry.md` - Feature tracking
- `docs/decision_log.md` - Architecture decision records
- `docs/regression_checklist.md` - Manual test procedures
