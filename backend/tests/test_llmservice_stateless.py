import asyncio

import pytest

from app.llm_service import LLMService


def test_llmservice_init_rejects_request_id_argument():
    with pytest.raises(TypeError):
        LLMService(request_id="req-1")


def test_llmservice_has_no_request_id_attribute():
    service = LLMService()
    with pytest.raises(AttributeError):
        _ = service.request_id


def test_llmservice_pipeline_does_not_access_request_id():
    async def fake_ollama(image_bytes: bytes) -> tuple[str, str]:
        return "req-xyz", "{\"ok\": true}"

    async def run_pipeline(service: LLMService, image_bytes: bytes, request_id: str) -> tuple[str, str]:
        return await service.recognize_passport(image_bytes, request_id=request_id)

    service = LLMService(ollama_client=fake_ollama)
    request_id, payload = asyncio.run(run_pipeline(service, b"image", request_id="req-xyz"))
    assert request_id == "req-xyz"
    assert "ok" in payload
