import json

from app.ocr_v2 import build_passport_v2_response


def test_build_passport_v2_response_parses_fields_and_mrz():
    llm_payload = {
        "fields": {
            "document_number": {
                "value": "1234 567890",
                "confidence": 0.94,
                "text_type": "printed",
                "language": "ru",
                "zones": [{"page": 0, "x": 0.1, "y": 0.2, "w": 0.3, "h": 0.1}],
            },
            "last_name": {"value": "IVANOV", "confidence": 0.91},
            "first_name": {"value": "IVAN", "confidence": 0.7},
            "date_of_birth": {"value": "1990-01-02", "confidence": 0.6},
        },
        "mrz": {
            "lines": ["P<RUSIVANOV<<IVAN<<<<<<<<<<<<<<<<<<<", "1234567890RUS9001025M3001012<<<<<<<<<<<<<<04"],
            "document_number": "1234567890",
            "date_of_birth": "900102",
            "date_of_expiry": "300101",
            "confidence": 0.88,
        },
        "raw_text": "raw-ocr-text",
    }

    response = build_passport_v2_response("req-1", json.dumps(llm_payload))

    assert response["status"] == "ok"
    assert response["fields"]["document_number"]["value"] == "1234567890"
    assert response["fields"]["document_number"]["confidence"] == 0.94
    assert response["fields"]["document_number"]["text_type"] == "printed"
    assert response["fields"]["document_number"]["language"] == "ru"
    assert response["fields"]["last_name"]["value"] == "IVANOV"
    assert response["mrz"]["document_number"]["value"] == "1234567890"
    assert response["mrz"]["date_of_birth"]["value"] == "1990-01-02"
    assert response["zones"][0]["field"] == "document_number"
    assert response["raw"]["raw_text"] == "raw-ocr-text"
    assert response["model_confidence"] == 0.6


def test_build_passport_v2_response_handles_invalid_json():
    response = build_passport_v2_response("req-2", "not-json")

    assert response["status"] == "error"
    assert response["errors"][0]["code"] == "PARSE_ERROR"
    assert response["fields"]["document_number"]["value"] is None


def test_build_passport_v2_response_pessimistic_confidence():
    llm_payload = {
        "fields": {
            "document_number": {"value": "123456789", "confidence": 0.8},
        },
        "fields_confidence": {
            "document_number": 0.4,
        },
    }

    response = build_passport_v2_response("req-3", json.dumps(llm_payload))

    assert response["fields"]["document_number"]["confidence"] == 0.4
    assert response["model_confidence"] == 0.4
