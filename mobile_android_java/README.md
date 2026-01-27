Android Java demo. Open in Android Studio and run.

## Flow
- Приложение использует машинку состояний: `CAMERA → PHOTO_SENDING → NFC_WAIT → NFC_READING → RESULT/ERROR`.
- CAMERA: отображается превью документа и кнопка съемки; запускается `ACTION_IMAGE_CAPTURE` с записью во временный файл через `FileProvider`.
- PHOTO_SENDING: фото читается с диска, проверяется, что размер > 500KB, затем отправляется в сервис распознавания; кнопка блокируется.
- NFC_WAIT: ожидание NFC-сканирования, включается NFC-dispatch.
- NFC_READING: чтение NFC, отображается статус процесса.
- RESULT/ERROR: показываются распознанные поля или сообщение об ошибке с возможностью повторить процесс.

## Tests
- Unit и integration tests находятся в `app/src/test/java` (включая проверки чтения файлов для фото).
- Запуск: `gradlew test`
