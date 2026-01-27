# Регрессионный чек-лист: MRZ → NFC → /nfc → face.jpg

## Сквозной сценарий (ручной)
1. Ввести `document_number` + даты (в любом допустимом формате) вручную.
   - Ожидаемо: мобильное приложение нормализует и показывает `YYMMDD` в состоянии/отладке.
   - Ожидаемо: при возврате в `CAMERA`-state превью камеры не остается пустым (при наличии permissions).
2. Запустить NFC-сценарий и дождаться результата.
   - Ожидаемо: чтение NFC выполняется в background (UI не блокируется).
   - Ожидаемо: есть ненулевой `faceImageJpeg` до отправки на backend.
   - Если нет — понятная ошибка, запрос `/nfc` не отправляется.
3. POST `/nfc`.
   - Ожидаемо: ответ содержит `scan_id`, `face_image_url`, `passport` (нормализованный).
   - Debug на экране показывает полный JSON ответа.
4. GET `face_image_url`.
   - Ожидаемо: `image/jpeg`, фото отображается в приложении.
5. Web UI (если используется): дождаться SSE `nfc_scan_success`.
   - Ожидаемо: событие содержит `face_image_url`, изображение показывается.

## Канонические форматы

| Поле | Канон | Допустимые входные форматы (backend) | Нормализация |
|---|---|---|---|
| `document_number` | `A-Z0-9<` (строка) | `string` | trim + remove spaces + uppercase (padding/length: без доп. паддинга, длина сохраняется) |
| `date_of_birth` | `YYMMDD` | `YYMMDD`, `YYYYMMDD`, `YYYY-MM-DD` | в `YYMMDD` |
| `date_of_expiry` | `YYMMDD` | `YYMMDD`, `YYYYMMDD`, `YYYY-MM-DD` | в `YYMMDD` |
| `face_image_b64` | base64(JPEG), non-empty | только non-empty base64 | пустое значение запрещено |

Примечания:
- `JPEG2000/JP2` допускается доставлять на backend для конвертации в JPEG; если конвертация невозможна, возвращать 422 и не сохранять файл.
- Клиент отправляет байты лица без предварительной конвертации и обрабатывает 422 от backend как ошибку чтения.

## Матрица совместимости (backend обязан принимать)

### New (preferred)
```json
{
  "passport": {
    "mrz": {
      "document_number": "string",
      "date_of_birth": "string (accepts YYYY-MM-DD/YYMMDD/...)",
      "date_of_expiry": "string (accepts YYYY-MM-DD/YYMMDD/...)"
    }
  },
  "face_image_b64": "base64 jpeg (non-empty)"
}
```

### Legacy (top-level mrz)
```json
{
  "mrz": {
    "document_number": "string",
    "date_of_birth": "string (accepts YYYY-MM-DD/YYMMDD/...)",
    "date_of_expiry": "string (accepts YYYY-MM-DD/YYMMDD/...)"
  },
  "face_image_b64": "base64 jpeg (non-empty)"
}
```
Поведение: backend преобразует к `passport.mrz` и хранит нормализованный `passport`.

### Legacy (flat passport fields)
```json
{
  "passport": {
    "document_number": "string",
    "date_of_birth": "string",
    "date_of_expiry": "string"
  },
  "face_image_b64": "base64 jpeg (non-empty)"
}
```
Поведение: backend переносит поля в `passport.mrz` и хранит нормализованный `passport`.

## Блокеры (критические дефекты)
- Отсутствует `scan_id` или `face_image_url` в ответе `/nfc`.
- `face_image_url` в SSE `nfc_scan_success` отсутствует.
- `face_image_b64` пустой или невалидный (клиент пропускает, сервер принимает 200).
- Ответ `/recognize` возвращает даты не в `YYMMDD`.
- `GET /nfc/{scan_id}/face.jpg` не возвращает `image/jpeg` или тело пустое.
- Потеря/искажение ключей MRZ (`document_number`, `date_of_birth`, `date_of_expiry`).

## API request logs
- Для JSON запросов/ответов сохраняются полные `request_body`/`response_body` (включая StreamingResponse с `application/json`).
- Для `text/event-stream`, multipart и бинарных ответов сохраняется placeholder (без буферизации тела).
- При превышении лимита тела используется placeholder с размером.

## NFC тайминги
- IsoDep timeout: 45000 мс (чтение не блокирует UI-поток).

## Backend env compatibility
- Backend читает `BACKEND_HOST`/`BACKEND_PORT` и `OLLAMA_TIMEOUT_SECONDS` с fallback на `APP_HOST`/`APP_PORT` и `OLLAMA_TIMEOUT_SEC`.
- Проверка: `backend/tests/test_settings_env.py`, `backend/tests/test_settings_env_integration.py`.

## SSE маршруты
- Канонический: `/api/events`.
- Legacy: `/events` (должен работать).
- `/api/api/events` не должен появляться.

## Обязательные тесты
См. `backend/tests/test_contracts_regression.py`, `mobile_android_java/app/src/test/java/com/demo/passport/DateNormalizeTest.java`,
`mobile_android_java/app/src/test/java/com/demo/passport/MainActivityTest.java`.
