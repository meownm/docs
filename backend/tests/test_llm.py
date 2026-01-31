from app.llm import build_prompt_for_passport_recognition, build_prompt_for_passport_v2


def test_build_prompt_for_passport_recognition_mentions_mrz_keys():
    prompt = build_prompt_for_passport_recognition()
    assert "Return ONLY a JSON object with the exact keys" in prompt
    assert "document_number" in prompt
    assert "date_of_birth" in prompt
    assert "date_of_expiry" in prompt


def test_build_prompt_for_passport_v2_mentions_full_fields():
    prompt = build_prompt_for_passport_v2()
    assert '"fields"' in prompt
    assert '"document_number"' in prompt
    assert '"last_name"' in prompt
    assert '"date_of_birth"' in prompt
    assert '"mrz"' in prompt
    assert "text_type" in prompt
    assert "language" in prompt
