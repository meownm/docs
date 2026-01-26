# Реестр требований

Статусы: proposed | implemented | verified

| ID | Требование | Статус | Артефакт |
|---|---|---|---|
| R-01 | Состав: web-интерфейс, мобилка, backend | implemented | backend/app/static, mobile_android_java |
| R-02 | Мобилка фотографирует и отправляет фото на backend | implemented | mobile_android_java/app/.../MainActivity.java |
| R-03 | Backend принимает фото и распознаёт загранпаспорт через мультимодальную LLM | implemented | backend/app/main.py, llm.py |
| R-04 | LLM через Ollama, модель qwen3-vl:30b | implemented | backend/app/settings.py, llm.py |
| R-05 | API: принять картинку, вернуть параметры заграна либо ошибку | implemented | POST /api/passport/recognize |
| R-06 | Мобилка при 200 показывает данные и предлагает NFC | implemented | MainActivity.java |
| R-07 | Мобилка NFC: извлекает параметры и фото, отправляет в backend | implemented (каркас) | NfcPassportReader.java + POST /api/passport/nfc |
| R-08 | Backend сохраняет NFC данные, web получает сигнал и показывает фото | implemented | nfc_scans + SSE /api/events + web UI |
| R-09 | Мобилка на Java | implemented | mobile_android_java |
| R-10 | Логировать входы/выходы LLM в локальной SQLite | implemented | llm_logs table + insert in recognize |
| R-11 | Проект демонстрационный, без развития | implemented | README.md |
