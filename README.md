# Demo: Passport Photo + NFC Flow (Mobile + Backend + Web)

Демонстрационный проект из трёх частей:

1. **backend** — принимает фото паспорта, распознаёт через мультимодальную LLM (Ollama + `qwen3-vl:30b`), принимает результат NFC‑скана, сохраняет и пушит событие в web.
2. **web** — простой интерфейс (внутри backend): показывает статус и фото из NFC‑чипа после успешного сканирования.
3. **mobile (Android, Java)** — показывает превью, отправляет фото на backend, отображает распознанные параметры, затем считывает NFC (eMRTD) и отправляет параметры + фото на backend.

Проект демонстрационный и не предполагает дальнейшего развития.

> Репозиторий хранит только исходники. Артефакты сборки и служебные файлы (например, `.gradle` и `mobile_android_java/build`) должны оставаться локальными и не коммитятся.

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
> Подробный контракт NFC см. в `docs/contracts/nfc.md`.
> Регрессионный чек-лист: `docs/regression_checklist.md`.

### POST `/recognize`
Вход: `multipart/form-data` с полем `image`.

Выход 200 (успех — MRZ поля):
```json
{
  "document_number": "123456789",
  "date_of_birth": "900131",
  "date_of_expiry": "300131"
}
```
Даты всегда возвращаются в формате `YYMMDD` (канон для mobile).

Выход 200 (ошибка — строка):
```json
{ "error": "MRZ not found in recognition result" }
```

### POST `/nfc`
Вход: JSON (обязательные поля):
- `passport` — объект, не пустой.
- `face_image_b64` — строка с base64 (обязательная, валидная).
Сервер принимает даты MRZ в форматах `YYMMDD`, `YYYYMMDD`, `YYYY-MM-DD` и нормализует к `YYMMDD`.

Выход 200:
```json
{
  "scan_id": "uuid",
  "face_image_url": "/api/nfc/<scan_id>/face.jpg",
  "passport": { "document_number": "...", "date_of_birth": "YYMMDD", "date_of_expiry": "YYMMDD" }
}
```

Выход 422 (ошибка — строка):
```json
{ "detail": "Invalid passport" }
```

```json
{ "detail": "Invalid face_image_b64: ..." }
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

### POST `/errors`
Вход: JSON (обязательные поля):
- `platform` — строка (ios/android/web).
- `error_message` — строка.
Опционально: `ts_utc`, `app_version`, `stacktrace`, `context_json`, `user_agent`, `device_info`, `request_id`.

Выход 200:
```json
{
  "status": "stored",
  "id": 123
}
```
Ошибка 422 (валидация обязательных полей):
```json
{
  "detail": [
    {
      "loc": ["body", "platform"],
      "msg": "Field required",
      "type": "missing"
    }
  ]
}
```

### Swagger/Web
Все endpoints доступны также с префиксом `/api`, например: `/api/recognize`, `/api/nfc`, `/api/events`, `/api/errors`.

## Логирование входов/выходов LLM и NFC
Backend сохраняет вход/выход LLM в SQLite: `backend/data/app.db`, таблица `llm_logs`.
Сохраняемые поля: `request_id`, `model`, `input_json`, `output_json`, `success`, `error`, `ts_utc`.
NFC-сканы сохраняются в таблице `nfc_scans` с полями: `scan_id`, `ts_utc`, `passport_json`, `face_image_path`.

## Логирование ошибок приложения
Backend сохраняет логи ошибок приложений в SQLite: `backend/data/app.db`, таблица `app_error_logs`.
Web UI отправляет ошибки `window.onerror`, `unhandledrejection` и ошибки SSE-подключения в `/api/errors` (с базовым debounce, чтобы не спамить при потере сети).
Android `BackendApi.reportError(...)` отправляет ошибки в `/errors` и вызывается из `onFailure`/`onError` с контекстом запроса, кодом ответа и текстом ошибки.

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

Web UI (unit/integration логика камеры):

```bash
node web/tests/run_tests.js
```

## Проверка NFC на устройстве (manual)
1. Подготовьте MRZ (ручной ввод или результат распознавания).
2. Запустите сканирование NFC и поднесите паспорт к телефону.
3. После успешного BAC приложение читает DG1 и DG2, отправляет payload в backend и скачивает фото по `face_image_url`.
4. Проверьте, что UI показывает фото и в отладочной секции сохранены ответы `/recognize`, `/nfc` и `/nfc/{scan_id}/face.jpg`.
