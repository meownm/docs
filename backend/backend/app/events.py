import asyncio
from dataclasses import dataclass
from typing import AsyncIterator, Dict, Optional


@dataclass
class Event:
    event_type: str
    data: Dict


class EventBus:
    def __init__(self) -> None:
        self._queue: "asyncio.Queue[Event]" = asyncio.Queue()
        self._last: Optional[Event] = None

    async def publish(self, event: Event) -> None:
        self._last = event
        await self._queue.put(event)

    async def stream(self) -> AsyncIterator[Event]:
        # On connect, immediately yield the last known event (if any) to make UI recoverable.
        if self._last is not None:
            yield self._last
        while True:
            e = await self._queue.get()
            yield e


bus = EventBus()
