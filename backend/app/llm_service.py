from __future__ import annotations

from typing import Awaitable, Callable

from app.llm import ollama_chat_with_image


class LLMService:
    """LLM service wrapper.

    Note: request_id is pipeline-scoped and must be passed into methods.
    """

    __slots__ = ("_ollama_client",)

    def __init__(
        self,
        *,
        ollama_client: Callable[[bytes], Awaitable[tuple[str, str]]] = ollama_chat_with_image,
    ) -> None:
        self._ollama_client = ollama_client

    async def recognize_passport(
        self,
        image_bytes: bytes,
        *,
        request_id: str | None,
    ) -> tuple[str, str]:
        return await self._ollama_client(image_bytes)
