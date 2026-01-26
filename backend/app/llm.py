
def build_prompt(lang: str) -> str:
    if lang.lower().startswith("ru"):
        return """
Ты распознаёшь загранпаспорт (eMRTD).
Извлеки BAC/MRZ keys.

Верни JSON:

{
  "document_number": "...",
  "date_of_birth": "YYYY-MM-DD",
  "date_of_expiry": "YYYY-MM-DD"
}

или

{
  "error": {
    "code": "MRZ_NOT_FOUND",
    "message": "..."
  }
}
"""
    return """
You parse a passport (eMRTD).
Extract BAC/MRZ keys.

Return JSON or error.
"""
