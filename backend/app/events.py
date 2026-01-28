from __future__ import annotations

import asyncio
from typing import AsyncIterator, Dict, Any, Set


class EventBus:
    """Broadcast event bus supporting multiple SSE subscribers."""

    def __init__(self) -> None:
        self._subscribers: Set[asyncio.Queue[Dict[str, Any]]] = set()
        self._lock = asyncio.Lock()

    async def publish(self, event: Dict[str, Any]) -> None:
        """Publish event to all subscribers."""
        async with self._lock:
            for queue in self._subscribers:
                try:
                    queue.put_nowait(event)
                except asyncio.QueueFull:
                    pass  # Drop event if subscriber queue is full

    async def subscribe(self) -> AsyncIterator[Dict[str, Any]]:
        """Subscribe to events. Each subscriber gets all published events."""
        queue: asyncio.Queue[Dict[str, Any]] = asyncio.Queue(maxsize=100)
        async with self._lock:
            self._subscribers.add(queue)
        try:
            while True:
                event = await queue.get()
                yield event
        finally:
            async with self._lock:
                self._subscribers.discard(queue)


event_bus = EventBus()
