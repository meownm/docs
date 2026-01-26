from app.llm import build_prompt


def test_build_prompt_english_contains_json_schema():
    prompt = build_prompt("en")
    assert "Return STRICT JSON only" in prompt
    assert '{"document_number":"...","date_of_birth":"YYYY-MM-DD","date_of_expiry":"YYYY-MM-DD"}' in prompt
    assert '"error":{"code":"MRZ_NOT_FOUND","message":"..."}' in prompt
