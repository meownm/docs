from __future__ import annotations

import json
import time
from datetime import datetime, timezone

from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.responses import Response, StreamingResponse

from app.db import get_db


class ApiRequestLoggingMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        start = time.perf_counter()
        error_text = None
        status_code = None
        response_body = None
        response_headers = None

        request_body_bytes = await request.body()
        request_body = _format_body(
            request_body_bytes,
            request.headers.get("content-type"),
        )

        try:
            response = await call_next(request)
            status_code = response.status_code
            response_headers = dict(response.headers)
            response_headers.pop("content-length", None)
            content_type = response.headers.get("content-type") or response.media_type
            if isinstance(response, StreamingResponse):
                if _is_event_stream(response):
                    response_body = _format_streaming_placeholder(response)
                    return response

                if _should_capture_stream_body(content_type):
                    response_body_bytes = await _collect_response_body(response)
                    response_body = _format_body(response_body_bytes, content_type)
                    return Response(
                        content=response_body_bytes,
                        status_code=response.status_code,
                        headers=response_headers,
                        media_type=response.media_type,
                        background=response.background,
                    )

                response_body = _format_streaming_placeholder(response)
                return response

            response_body_bytes = await _collect_response_body(response)
            response_body = _format_body(response_body_bytes, content_type)
            return Response(
                content=response_body_bytes,
                status_code=response.status_code,
                headers=response_headers,
                media_type=response.media_type,
                background=response.background,
            )
        except Exception as e:
            error_text = str(e)
            status_code = 500
            response_body = json.dumps(
                {"placeholder": "unhandled_exception", "detail": error_text},
                ensure_ascii=False,
            )
            raise
        finally:
            duration_ms = int((time.perf_counter() - start) * 1000)

            async with get_db() as conn:
                await conn.execute(
                    """
                    INSERT INTO api_request_logs (
                        ts_utc,
                        method,
                        path,
                        query,
                        status_code,
                        duration_ms,
                        client_ip,
                        user_agent,
                        content_type,
                        content_length,
                        error,
                        request_body,
                        response_body
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        datetime.now(timezone.utc).isoformat(),
                        request.method,
                        request.url.path,
                        request.url.query,
                        status_code,
                        duration_ms,
                        request.client.host if request.client else None,
                        request.headers.get("user-agent"),
                        request.headers.get("content-type"),
                        int(request.headers.get("content-length"))
                        if request.headers.get("content-length") else None,
                        error_text,
                        request_body,
                        response_body,
                    ),
                )
                await conn.commit()


MAX_BODY_SIZE = 65536
TEXT_CONTENT_TYPES = (
    "application/json",
    "application/x-www-form-urlencoded",
)


def _format_body(body: bytes, content_type: str | None) -> str:
    if body is None:
        return ""

    size = len(body)
    if size == 0:
        return ""

    if size > MAX_BODY_SIZE:
        return _placeholder_body(size)

    content_type = (content_type or "").lower()
    if content_type.startswith("multipart/"):
        return _placeholder_body(size)

    is_textual = content_type.startswith("text/")
    is_json_or_form = any(content_type.startswith(ct) for ct in TEXT_CONTENT_TYPES)
    if not (is_textual or is_json_or_form):
        return _placeholder_body(size)

    try:
        return body.decode("utf-8")
    except UnicodeDecodeError:
        return _placeholder_body(size)


def _placeholder_body(size: int) -> str:
    return json.dumps(
        {"placeholder": "binary_or_too_large", "size": size},
        ensure_ascii=False,
    )


def _format_streaming_placeholder(response: Response) -> str:
    content_length = response.headers.get("content-length")
    size = int(content_length) if content_length and content_length.isdigit() else 0
    return _placeholder_body(size)


def _is_event_stream(response: Response) -> bool:
    content_type = response.headers.get("content-type") or response.media_type or ""
    return content_type.lower().startswith("text/event-stream")


def _should_capture_stream_body(content_type: str | None) -> bool:
    content_type = (content_type or "").lower()
    if content_type.startswith("multipart/"):
        return False
    if content_type.startswith("text/"):
        return True
    return any(content_type.startswith(ct) for ct in TEXT_CONTENT_TYPES)


async def _collect_response_body(response: Response) -> bytes:
    if getattr(response, "body_iterator", None) is not None:
        chunks = [chunk async for chunk in response.body_iterator]
        return b"".join(chunks)
    body = getattr(response, "body", b"")
    return body if body is not None else b""
