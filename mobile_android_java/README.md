Android Java demo. Open in Android Studio and run.

## Flow
- Приложение использует машинку состояний: `CAMERA → PHOTO_SENDING → NFC_WAIT → NFC_READING → RESULT/ERROR`.
- CAMERA: отображается live-превью через CameraX `PreviewView` и кнопка съемки; при входе в состояние биндим превью камеры, затем запускается `ACTION_IMAGE_CAPTURE` с записью во временный файл через `FileProvider`.
- PHOTO_SENDING: фото читается с диска, проверяется, что размер > 500KB, затем отправляется в сервис распознавания; кнопка блокируется.
- NFC_WAIT: ожидание NFC-сканирования после успешного ответа 200 от распознавания; включается NFC-dispatch.
- NFC_READING: чтение NFC (через `NfcPassportReader`), сбор payload и отправка в backend. Если MRZ-ключи отсутствуют, чтение NFC завершится ошибкой.
- RESULT/ERROR: при ответе 200 от `/nfc` показываются распознанные поля; при ошибке показывается видимое сообщение; NFC остаётся выключенным при ошибке.

## Backend API
- Базовый URL задается через `BackendConfig.getBaseUrl()`.
- Распознавание фото: `POST {baseUrl}/recognize`, content-type `multipart/form-data`.
- NFC payload: `POST {baseUrl}/nfc`, content-type `application/json`.
  - payload включает `passport` и `face_image_b64` (base64 JPEG лица из NFC).

## Tests
- Unit и integration tests находятся в `app/src/test/java` (включая проверки чтения файлов для фото).
- Запуск: `gradlew test`
