Android Java demo. Open in Android Studio and run.

## Flow
- Приложение использует машинку состояний: `CAMERA → PHOTO_SENDING → NFC_WAIT → NFC_READING → RESULT/ERROR`.
- CAMERA: отображается превью документа и кнопка съемки.
- PHOTO_SENDING: фото отправляется в сервис распознавания, кнопка блокируется.
- NFC_WAIT: ожидание NFC-сканирования, включается NFC-dispatch.
- NFC_READING: чтение NFC, отображается статус процесса.
- RESULT/ERROR: показываются распознанные поля или сообщение об ошибке с возможностью повторить процесс.

## Backend API
- Базовый URL задается через `BackendConfig.getBaseUrl()`.
- Распознавание фото: `POST {baseUrl}/recognize`, content-type `multipart/form-data`.
- NFC payload: `POST {baseUrl}/nfc`, content-type `application/json`.

## Tests
- Unit и integration tests находятся в `app/src/test/java`.
- Запуск: `gradlew test`
