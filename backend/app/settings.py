from __future__ import annotations

import os
from dataclasses import dataclass


def _env(key: str, default: str) -> str:
    v = os.getenv(key)
    return v if v is not None and v != "" else default


def _env_int(key: str, default: int) -> int:
    v = os.getenv(key)
    if v is None or v == "":
        return default
    return int(v)


@dataclass(frozen=True)
class Settings:
    # Server
    host: str = _env("APP_HOST", "127.0.0.1")
    port: int = _env_int("APP_PORT", 30450)

    # Files
    files_dir: str = _env("FILES_DIR", "./files")

    # Ollama
    ollama_base_url: str = _env("OLLAMA_BASE_URL", "http://127.0.0.1:11434")
    ollama_model: str = _env("OLLAMA_MODEL", "qwen3-vl:30b")
    ollama_timeout_sec: int = _env_int("OLLAMA_TIMEOUT_SEC", 120)


settings = Settings()
