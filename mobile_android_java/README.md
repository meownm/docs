Android Java demo. Open in Android Studio and run.

## Flow
- Приложение использует машинку состояний: `CAMERA → PHOTO_SENDING → NFC_WAIT → NFC_READING → RESULT/ERROR`.
- CAMERA: отображается live-превью через CameraX `PreviewView` и кнопка съемки; при входе в состояние биндим превью камеры через кешируемый `ProcessCameraProvider` future, затем запускается `ACTION_IMAGE_CAPTURE` с записью во временный файл через `FileProvider`. Authority формируется как `${applicationId}.fileprovider` и должен совпадать с `AndroidManifest.xml`.
- PHOTO_SENDING: фото читается с диска, проверяется, что размер > 500KB, затем отправляется в сервис распознавания; кнопка блокируется.
- NFC_WAIT: ожидание NFC-сканирования после успешного ответа 200 от распознавания **или после ручного ввода MRZ-полей и нажатия “Проверка через чип”**; перед переходом проверяется заполненность MRZ и формат дат `YYMMDD`, иначе показывается понятное сообщение и NFC не стартует; включается NFC-dispatch.
- NFC_READING: чтение NFC (через `NfcPassportReader`) с использованием реального `Tag`, сбор payload и отправка в backend. MRZ используется только для доступа (BAC) и не подставляется в `passport`; паспортные поля должны приходить из чипа. Если `Tag` отсутствует или чтение не реализовано, показывается ошибка и запрос `/nfc` не выполняется.
- RESULT/ERROR: при ответе 200 от `/nfc` показываются распознанные поля; при ошибке показывается видимое сообщение; NFC остаётся выключенным при ошибке.

## Backend API
- Базовый URL задается через `BackendConfig.getBaseUrl()`.
- Распознавание фото: `POST {baseUrl}/recognize`, content-type `multipart/form-data`.
- NFC payload: `POST {baseUrl}/nfc`, content-type `application/json`.
  - payload включает `passport` и `face_image_b64` (base64 JPEG лица из NFC).
- После каждого API-вызова приложение показывает полный raw-ответ backend (или текст ошибки сети/парсинга)
  в нижнем отладочном блоке экрана.

## Tests
- Unit tests находятся в `app/src/test/java` (включая проверки чтения файлов для фото и валидности authority).
- Instrumentation tests для проверки `FileProvider` и ручного ввода MRZ (включая негативные сценарии для неожиданных authority) находятся в `app/src/androidTest/java`.
- Запуск: `gradlew test` и `gradlew connectedAndroidTest`.

## FileProvider configuration
- В `build.gradle` включен `buildFeatures { buildConfig true }` для генерации `BuildConfig`.
- Authority для `FileProvider` строится от `getPackageName()` и должен совпадать с `android:authorities="${applicationId}.fileprovider"` в `AndroidManifest.xml`.
