# Demo: Passport Photo + NFC Flow (Mobile + Backend + Web)

Демонстрационный проект из трёх частей:

1. **backend** — принимает фото паспорта, распознаёт через мультимодальную LLM (Ollama + `qwen3-vl:30b`), принимает результат NFC‑скана, сохраняет и пушит событие в web.
2. **web** — простой интерфейс (внутри backend): показывает статус и фото из NFC‑чипа после успешного сканирования.
3. **mobile (Android, Java)** — показывает превью, отправляет фото на backend, отображает распознанные параметры, затем считывает NFC (eMRTD) и отправляет параметры + фото на backend.

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
Этот каталог является единственным источником истины для backend.

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
Foreground dispatch NFC включается только на этапе ожидания сканирования (NFC_WAIT) и отключается при выходе из этого состояния.

## Контракты API (канон для mobile)

> Для мобильной команды используйте пути без `/api`.
> Префикс `/api` нужен только для Swagger/Web и повторяет те же обработчики.

### POST `/recognize`
Вход: `multipart/form-data` с полем `image`.

Выход 200 (успех — MRZ поля):
```json
{
  "document_number": "123456789",
  "date_of_birth": "19900131",
  "date_of_expiry": "20300131"
}
```

Выход 200 (ошибка — строка):
```json
{ "error": "MRZ not found in recognition result" }
```

### POST `/nfc`
Вход: JSON (параметры) + фото (base64).

Выход 200:
```json
{
  "scan_id": "uuid"
}
```

Выход 200 (ошибка — строка):
```json
{ "error": "Invalid face_image_b64: ..." }
```

### GET `/events`
SSE поток. При сохранении NFC‑скана отправляется событие `nfc_scan_success` в SSE-формате:

```
event: nfc_scan_success
data: {"type":"nfc_scan_success","scan_id":"...","face_image_url":"/api/nfc/<scan_id>/face.jpg","passport":{...}}
```

Поле `passport` присутствует только если было отправлено в запросе NFC.

### GET `/nfc/{scan_id}/face.jpg`
Возвращает фото лица из NFC‑чипа.

### Swagger/Web
Дублирующие пути с `/api`, например: `/api/recognize`, `/api/nfc`, `/api/events`.
Дополнительно доступны `/passport/recognize` и `/passport/nfc` (и их варианты с `/api`).

## Логирование входов/выходов LLM
Backend сохраняет вход/выход LLM в SQLite: `backend/data/app.db`, таблица `llm_logs`.

## Тесты
Backend использует `pytest` для позитивных/негативных и интеграционных сценариев. Запуск:

```bat
cd backend
poetry install
poetry run pytest
```

Android (unit/integration tests в `mobile_android_java/`):

```bat
cd mobile_android_java
gradlew test
```
