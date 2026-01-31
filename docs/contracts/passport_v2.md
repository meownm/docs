# Passport OCR v2 контракт

## Endpoint
`POST /api/ocr/passport/v2`

> Важно: v1 эндпоинт `/recognize` не меняется и продолжает работать для мобилки.

## Запрос
`multipart/form-data`

| Поле | Тип | Обязательное | Описание |
| --- | --- | --- | --- |
| image | file | да | Фото страницы паспорта |

## Ответ (structured JSON)
```json
{
  "request_id": "uuid",
  "status": "ok",
  "document_type": "passport",
  "model_confidence": 0.72,
  "fields": {
    "document_number": { "value": "123456789", "confidence": 0.92, "text_type": "printed", "language": "ru", "zones": [] },
    "document_series": { "value": "1234", "confidence": 0.6, "zones": [] },
    "last_name": { "value": "IVANOV", "confidence": 0.88, "zones": [] },
    "first_name": { "value": "IVAN", "confidence": 0.88, "zones": [] },
    "middle_name": { "value": null, "confidence": 0.0, "zones": [] },
    "date_of_birth": { "value": "1990-01-01", "confidence": 0.74, "zones": [] },
    "place_of_birth": { "value": "MOSCOW", "confidence": 0.7, "zones": [] },
    "gender": { "value": "M", "confidence": 0.9, "zones": [] },
    "nationality": { "value": "RUS", "confidence": 0.9, "zones": [] },
    "date_of_issue": { "value": "2015-01-01", "confidence": 0.6, "zones": [] },
    "date_of_expiry": { "value": "2025-01-01", "confidence": 0.8, "zones": [] },
    "issuing_authority": { "value": "MVD", "confidence": 0.6, "zones": [] },
    "issuing_country": { "value": "RUS", "confidence": 0.7, "zones": [] },
    "personal_number": { "value": null, "confidence": 0.0, "zones": [] }
  },
  "mrz": {
    "lines": { "value": ["P<RUSIVANOV<<IVAN<<<<<<<<<<<<<<<<<<<", "1234567890RUS9001015M2501012<<<<<<<<<<<<<<04"], "confidence": 0.8 },
    "document_number": { "value": "1234567890", "confidence": 0.8 },
    "date_of_birth": { "value": "1990-01-01", "confidence": 0.8 },
    "date_of_expiry": { "value": "2025-01-01", "confidence": 0.8 }
  },
  "zones": [
    { "field": "document_number", "page": 0, "x": 0.12, "y": 0.42, "w": 0.3, "h": 0.08 }
  ],
  "checks": [
    { "code": "mrz_document_number_match", "status": "ok", "message": "Номер документа совпадает с MRZ" }
  ],
  "errors": [],
  "raw": {
    "llm_text": "...",
    "parsed_json": {},
    "parse_error": null,
    "raw_text": "..."
  }
}
```

## Поля
Все поля возвращаются в формате `{ value, confidence, text_type, language, zones }`. Если значение не найдено, `value = null`, `confidence = 0.0`.

### `fields`
| Поле | Тип значения | Описание |
| --- | --- | --- |
| document_number | string | Номер документа |
| document_series | string | Серия документа |
| last_name | string | Фамилия |
| first_name | string | Имя |
| middle_name | string | Отчество |
| date_of_birth | string (YYYY-MM-DD) | Дата рождения |
| place_of_birth | string | Место рождения |
| gender | string | Пол |
| nationality | string | Гражданство |
| date_of_issue | string (YYYY-MM-DD) | Дата выдачи |
| date_of_expiry | string (YYYY-MM-DD) | Дата окончания |
| issuing_authority | string | Орган выдачи |
| issuing_country | string | Страна выдачи |
| personal_number | string | Личный номер (если есть) |

### `model_confidence`
Интегральная пессимистичная оценка качества распознавания (0..1). Рассчитывается по минимальной уверенности среди распознанных значений.

### `text_type`
Тип текста в поле: `printed`, `handwritten`, `unknown`.

### `language`
Язык текста, если удалось определить (например, `ru`, `en`).

### `mrz`
Блок MRZ возвращается, если распознаны строки MRZ или поля документа.

### `zones`
Список зон на изображении в относительных координатах (0..1) или абсолютных (как вернёт OCR). Используется UI для подсветки.

### `checks`
Массив проверок (например, совпадение MRZ и визуальных полей).

### `errors`
Список ошибок распознавания (например, `PARSE_ERROR`, `LLM_UNAVAILABLE`, `EMPTY_IMAGE`).

## Использование в Web UI
Web UI отображает полный список полей, уверенность (confidence), MRZ-блок и ошибки/проверки по результатам `/api/ocr/passport/v2`.
