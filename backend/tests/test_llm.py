from app.llm import build_prompt_for_passport_recognition


def test_build_prompt_for_passport_recognition_mentions_mrz_keys():
    prompt = build_prompt_for_passport_recognition()
    assert "Return ONLY a JSON object with the exact keys" in prompt
    assert "document_number" in prompt
    assert "date_of_birth" in prompt
    assert "date_of_expiry" in prompt
