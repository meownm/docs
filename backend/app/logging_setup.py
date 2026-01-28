from __future__ import annotations

import logging
from typing import Any

DEFAULT_REQUEST_ID = "n/a"


def log_event(
    logger: logging.Logger,
    message: str,
    *,
    plane: str,
    extra: dict[str, Any] | None = None,
    request_id: str | None = None,
) -> None:
    """Emit a structured log message ensuring request_id is always present."""
    extra_payload = dict(extra or {})
    extra_payload.setdefault("plane", plane)
    resolved_request_id = extra_payload.get("request_id") or request_id or DEFAULT_REQUEST_ID
    extra_payload["request_id"] = resolved_request_id
    logger.info(message, extra=extra_payload)
