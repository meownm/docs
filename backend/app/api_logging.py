from __future__ import annotations

import time
from datetime import datetime
from fastapi import Request
from starlette.middleware.base import BaseHTTPMiddleware

from app.db import get_db


class ApiRequestLoggingMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        start = time.perf_counter()
        error_text = None
        status_code = None

        try:
            response = await call_next(request)
            status_code = response.status_code
            return response
        except Exception as e:
            error_text = str(e)
            status_code = 500
            raise
        finally:
            duration_ms = int((time.perf_counter() - start) * 1000)

            with get_db() as conn:
                conn.execute(
                    """
                    INSERT INTO api_request_log (
                        ts,
                        method,
                        path,
                        query,
                        status_code,
                        duration_ms,
                        client_ip,
                        user_agent,
                        content_type,
                        content_length,
                        error
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        datetime.utcnow().isoformat(),
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
                    ),
                )
                conn.commit()
