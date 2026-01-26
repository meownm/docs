# Demo: Passport Photo + NFC Flow (Mobile + Backend + Web)

Демонстрационный проект из трёх частей:

1. **backend** — принимает фото паспорта, распознаёт через мультимодальную LLM (Ollama + `qwen3-vl:30b`), принимает результат NFC‑скана, сохраняет и пушит событие в web.
2. **web** — простой интерфейс (внутри backend): показывает статус и фото из NFC‑чипа после успешного сканирования.
3. **mobile (Android, Java)** — фотографирует, отправляет фото на backend, отображает распознанные параметры, затем считывает NFC (eMRTD) и отправляет параметры + фото на backend.

Проект демонстрационный и не предполагает дальнейшего развития.

## Быстрый старт (Windows)

### 1) Ollama
Установите Ollama и убедитесь, что он доступен по URL (по умолчанию `http://127.0.0.1:11434`).

Скачайте модель:
```bat
ollama pull qwen3-vl:30b
```

### 2) Backend (Poetry)
Перейдите в `backend/`.

```bat
install.bat
run_dev.bat
```

После запуска:
- Swagger: `http://localhost:%BACKEND_PORT%/docs`
- Web UI: `http://localhost:%BACKEND_PORT%/`

### 3) Mobile (Android, Java)
Проект мобильного приложения в `mobile_android_java/`.
Открывайте в Android Studio, соберите и запустите на устройстве с NFC.

В настройках приложения задайте URL backend (см. `BackendConfig.java`).

## Контракты API

### POST `/api/passport/recognize`
Вход: `multipart/form-data` с полем `image`.

Выход 200:
```json
{
  "request_id": "uuid",
  "mrz": {
    "document_number": "123456789",
    "date_of_birth": "1990-01-31",
    "date_of_expiry": "2030-01-31"
  },
  "raw": { "optional": "model_specific" }
}
```

Выход 422/500 — ошибка распознавания с полями `error_code`, `message`.

### POST `/api/passport/nfc`
Вход: JSON (параметры) + фото (base64) или multipart (см. OpenAPI).

Выход 200:
```json
{
  "scan_id": "uuid",
  "status": "stored"
}
```

### GET `/api/events`
SSE поток. При сохранении NFC‑скана отправляется событие `nfc_scan_success` с `scan_id` и `face_image_url`.

### GET `/api/nfc/{scan_id}/face.jpg`
Возвращает фото лица из NFC‑чипа.

## Логирование входов/выходов LLM
Backend сохраняет вход/выход LLM в SQLite: `backend/data/app.db`, таблица `llm_logs`.

