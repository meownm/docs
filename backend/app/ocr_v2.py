from __future__ import annotations

import json
import re
from datetime import datetime
from typing import Any


FIELD_DEFINITIONS: dict[str, str] = {
    "document_number": "Номер документа",
    "document_series": "Серия документа",
    "last_name": "Фамилия",
    "first_name": "Имя",
    "middle_name": "Отчество",
    "date_of_birth": "Дата рождения",
    "place_of_birth": "Место рождения",
    "gender": "Пол",
    "nationality": "Гражданство",
    "date_of_issue": "Дата выдачи",
    "date_of_expiry": "Дата окончания",
    "issuing_authority": "Орган выдачи",
    "issuing_country": "Страна выдачи",
    "personal_number": "Личный номер",
}

DATE_FIELDS = {"date_of_birth", "date_of_issue", "date_of_expiry"}
TEXT_TYPE_VALUES = {"printed", "handwritten", "unknown"}


def _normalize_confidence(value: Any) -> float:
    if value is None:
        return 0.0
    try:
        score = float(value)
    except (TypeError, ValueError):
        return 0.0
    return max(0.0, min(1.0, score))


def _normalize_document_number(value: Any) -> str | None:
    if value is None:
        return None
    return re.sub(r"\s+", "", str(value)).strip() or None


def _normalize_date_iso(value: Any) -> str | None:
    if value is None:
        return None
    normalized = str(value).strip()
    if not normalized:
        return None
    if re.fullmatch(r"\d{4}-\d{2}-\d{2}", normalized):
        return normalized
    if re.fullmatch(r"\d{2}\.\d{2}\.\d{4}", normalized):
        try:
            return datetime.strptime(normalized, "%d.%m.%Y").strftime("%Y-%m-%d")
        except ValueError:
            return normalized
    if re.fullmatch(r"\d{8}", normalized):
        try:
            return datetime.strptime(normalized, "%Y%m%d").strftime("%Y-%m-%d")
        except ValueError:
            return normalized
    if re.fullmatch(r"\d{6}", normalized):
        yy = int(normalized[:2])
        year = 1900 + yy if yy >= 50 else 2000 + yy
        return f"{year}-{normalized[2:4]}-{normalized[4:6]}"
    return normalized


def _normalize_zones(zones: Any) -> list[dict[str, Any]]:
    if not isinstance(zones, list):
        return []
    normalized = []
    for zone in zones:
        if not isinstance(zone, dict):
            continue
        try:
            x = float(zone.get("x"))
            y = float(zone.get("y"))
            w = float(zone.get("w"))
            h = float(zone.get("h"))
        except (TypeError, ValueError):
            continue
        normalized.append({
            "page": int(zone.get("page", 0)),
            "x": x,
            "y": y,
            "w": w,
            "h": h,
        })
    return normalized


def _normalize_text_type(value: Any) -> str:
    if not value:
        return "unknown"
    normalized = str(value).strip().lower()
    if normalized in TEXT_TYPE_VALUES:
        return normalized
    return "unknown"


def _normalize_language(value: Any) -> str | None:
    if value is None:
        return None
    normalized = str(value).strip()
    return normalized or None


def _parse_field_value(
    *,
    field_name: str,
    raw_value: Any,
    confidence_map: dict[str, Any],
) -> dict[str, Any]:
    value = None
    confidence = None
    zones: list[dict[str, Any]] = []

    if isinstance(raw_value, dict):
        value = raw_value.get("value") if "value" in raw_value else raw_value.get("text")
        confidence = raw_value.get("confidence") or raw_value.get("score")
        text_type = raw_value.get("text_type")
        language = raw_value.get("language")
        zones = _normalize_zones(raw_value.get("zones") or raw_value.get("zone"))
    else:
        value = raw_value
        confidence = confidence_map.get(field_name)
        text_type = None
        language = None

    if field_name in DATE_FIELDS:
        value = _normalize_date_iso(value)
    elif field_name == "document_number":
        value = _normalize_document_number(value)

    confidence_candidates = [confidence, confidence_map.get(field_name)]
    normalized_candidates = [
        _normalize_confidence(candidate)
        for candidate in confidence_candidates
        if candidate is not None
    ]
    normalized_confidence = min(normalized_candidates) if normalized_candidates else 0.0
    if value in (None, ""):
        normalized_confidence = 0.0
        value = None

    return {
        "value": value,
        "confidence": normalized_confidence,
        "zones": zones,
        "text_type": _normalize_text_type(text_type),
        "language": _normalize_language(language),
    }


def _extract_mrz_from_text(llm_text: str) -> dict[str, str] | None:
    patterns = {
        "document_number": r"document[_\s]?number[:=]\s*([A-Z0-9<]+)",
        "date_of_birth": r"date[_\s]?of[_\s]?birth[:=]\s*([0-9]{6}|[0-9]{8}|[0-9]{4}-[0-9]{2}-[0-9]{2})",
        "date_of_expiry": r"date[_\s]?of[_\s]?expiry[:=]\s*([0-9]{6}|[0-9]{8}|[0-9]{4}-[0-9]{2}-[0-9]{2})",
    }

    result: dict[str, str] = {}
    for key, pattern in patterns.items():
        match = re.search(pattern, llm_text, re.IGNORECASE)
        if not match:
            return None
        result[key] = match.group(1)
    return result


def _build_mrz_container(parsed: dict[str, Any], llm_text: str) -> dict[str, Any] | None:
    mrz = parsed.get("mrz") if isinstance(parsed.get("mrz"), dict) else None
    raw_confidence = None
    lines = []
    if mrz:
        raw_confidence = mrz.get("confidence")
        raw_lines = mrz.get("lines") or mrz.get("raw_lines")
        if isinstance(raw_lines, list):
            lines = [str(line) for line in raw_lines if line]
    else:
        mrz = {}

    if not mrz:
        extracted = _extract_mrz_from_text(llm_text)
        if extracted:
            mrz.update(extracted)

    if not mrz and not lines:
        return None

    def _mrz_field(key: str) -> dict[str, Any]:
        value = mrz.get(key)
        if key in ("date_of_birth", "date_of_expiry"):
            value = _normalize_date_iso(value)
        elif key == "document_number":
            value = _normalize_document_number(value)
        return {
            "value": value,
            "confidence": _normalize_confidence(raw_confidence),
        }

    return {
        "lines": {
            "value": lines or None,
            "confidence": _normalize_confidence(raw_confidence),
        },
        "document_number": _mrz_field("document_number"),
        "date_of_birth": _mrz_field("date_of_birth"),
        "date_of_expiry": _mrz_field("date_of_expiry"),
    }


def _build_checks(fields: dict[str, Any], mrz: dict[str, Any] | None) -> list[dict[str, Any]]:
    checks: list[dict[str, Any]] = []
    if not mrz:
        return checks

    def compare(field_key: str, label: str) -> None:
        field_value = fields.get(field_key, {}).get("value")
        mrz_value = mrz.get(field_key, {}).get("value")
        if not field_value or not mrz_value:
            return
        status = "ok" if str(field_value) == str(mrz_value) else "warning"
        message = (
            f"{label} совпадает с MRZ"
            if status == "ok"
            else f"{label} не совпадает с MRZ"
        )
        checks.append({
            "code": f"mrz_{field_key}_match",
            "status": status,
            "message": message,
        })

    compare("document_number", "Номер документа")
    compare("date_of_birth", "Дата рождения")
    compare("date_of_expiry", "Дата окончания")

    return checks


def build_passport_v2_response(request_id: str, llm_text: str) -> dict[str, Any]:
    errors: list[dict[str, str]] = []
    parsed: dict[str, Any] = {}
    parse_error = None

    try:
        parsed = json.loads(llm_text)
        if not isinstance(parsed, dict):
            raise ValueError("JSON root is not an object")
    except Exception as exc:
        parse_error = str(exc)
        errors.append({
            "code": "PARSE_ERROR",
            "message": f"Failed to parse OCR response: {exc}",
        })
        parsed = {}

    fields_payload = parsed.get("fields") if isinstance(parsed.get("fields"), dict) else {}
    confidence_payload = (
        parsed.get("fields_confidence")
        if isinstance(parsed.get("fields_confidence"), dict)
        else {}
    )

    fields: dict[str, Any] = {}
    zones: list[dict[str, Any]] = []
    confidence_values: list[float] = []
    for field_name in FIELD_DEFINITIONS:
        raw_value = fields_payload.get(field_name)
        if raw_value is None and field_name in parsed:
            raw_value = parsed.get(field_name)
        field_entry = _parse_field_value(
            field_name=field_name,
            raw_value=raw_value,
            confidence_map=confidence_payload,
        )
        fields[field_name] = field_entry
        if field_entry["value"] is not None:
            confidence_values.append(field_entry["confidence"])
        for zone in field_entry.get("zones", []):
            zones.append({"field": field_name, **zone})

    zones.extend(_normalize_zones(parsed.get("zones")))

    mrz = _build_mrz_container(parsed, llm_text)
    if isinstance(mrz, dict):
        for key in ("lines", "document_number", "date_of_birth", "date_of_expiry"):
            entry = mrz.get(key)
            if isinstance(entry, dict) and entry.get("value") is not None:
                confidence_values.append(_normalize_confidence(entry.get("confidence")))
    checks = []
    if isinstance(parsed.get("checks"), list):
        checks.extend(parsed["checks"])
    checks.extend(_build_checks(fields, mrz))

    model_confidence = min(confidence_values) if confidence_values else 0.0
    response = {
        "request_id": request_id,
        "status": "error" if errors else "ok",
        "document_type": "passport",
        "model_confidence": model_confidence,
        "fields": fields,
        "mrz": mrz,
        "zones": zones,
        "checks": checks,
        "errors": errors,
        "raw": {
            "llm_text": llm_text,
            "parsed_json": parsed if parsed else None,
            "parse_error": parse_error,
            "raw_text": parsed.get("raw_text") if isinstance(parsed, dict) else None,
        },
    }
    return response


def build_passport_v2_error_response(
    request_id: str,
    *,
    code: str,
    message: str,
) -> dict[str, Any]:
    fields = {
        field_name: {
            "value": None,
            "confidence": 0.0,
            "zones": [],
            "text_type": "unknown",
            "language": None,
        }
        for field_name in FIELD_DEFINITIONS
    }
    return {
        "request_id": request_id,
        "status": "error",
        "document_type": "passport",
        "model_confidence": 0.0,
        "fields": fields,
        "mrz": None,
        "zones": [],
        "checks": [],
        "errors": [{"code": code, "message": message}],
        "raw": {
            "llm_text": None,
            "parsed_json": None,
            "parse_error": None,
            "raw_text": None,
        },
    }
