def build_prompt(lang: str) -> str:
    # Prompt is intentionally deterministic: tests rely on stable substrings.
    if lang.lower().startswith("ru"):
        return (
            "Ты распознаёшь загранпаспорт (eMRTD). Извлеки BAC/MRZ keys.\n"
            "Return STRICT JSON only.\n"
            '{"document_number":"...","date_of_birth":"YYYY-MM-DD","date_of_expiry":"YYYY-MM-DD"}\n'
            "or\n"
            '{"error":{"code":"MRZ_NOT_FOUND","message":"..."}}\n'
        )
    return (
        "You parse a passport (eMRTD). Extract BAC/MRZ keys.\n"
        "Return STRICT JSON only.\n"
        '{"document_number":"...","date_of_birth":"YYYY-MM-DD","date_of_expiry":"YYYY-MM-DD"}\n'
        "or\n"
        '{"error":{"code":"MRZ_NOT_FOUND","message":"..."}}\n'
    )
