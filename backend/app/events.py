from __future__ import annotations

import asyncio
from typing import AsyncIterator, Dict, Any


class EventBus:
    def __init__(self) -> None:
        self._queue: asyncio.Queue[Dict[str, Any]] = asyncio.Queue()

    async def publish(self, event: Dict[str, Any]) -> None:
        await self._queue.put(event)

    async def subscribe(self) -> AsyncIterator[Dict[str, Any]]:
        while True:
            event = await self._queue.get()
            yield event


event_bus = EventBus()
