import importlib


ENV_KEYS = [
    "APP_HOST",
    "APP_PORT",
    "BACKEND_HOST",
    "BACKEND_PORT",
    "OLLAMA_TIMEOUT_SEC",
    "OLLAMA_TIMEOUT_SECONDS",
]


def _load_settings(monkeypatch, env):
    import app.settings as settings_module

    for key in ENV_KEYS:
        monkeypatch.delenv(key, raising=False)
    for key, value in env.items():
        monkeypatch.setenv(key, value)
    importlib.reload(settings_module)
    return settings_module.Settings()


def test_settings_env_prefers_backend_vars(monkeypatch):
    settings = _load_settings(
        monkeypatch,
        {
            "APP_HOST": "10.0.0.1",
            "APP_PORT": "1111",
            "BACKEND_HOST": "0.0.0.0",
            "BACKEND_PORT": "2222",
            "OLLAMA_TIMEOUT_SEC": "120",
            "OLLAMA_TIMEOUT_SECONDS": "360",
        },
    )

    assert settings.host == "0.0.0.0"
    assert settings.port == 2222
    assert settings.ollama_timeout_sec == 360


def test_settings_env_falls_back_when_backend_empty(monkeypatch):
    settings = _load_settings(
        monkeypatch,
        {
            "APP_HOST": "10.0.0.2",
            "APP_PORT": "3333",
            "BACKEND_HOST": "",
            "BACKEND_PORT": "",
            "OLLAMA_TIMEOUT_SEC": "180",
            "OLLAMA_TIMEOUT_SECONDS": "",
        },
    )

    assert settings.host == "10.0.0.2"
    assert settings.port == 3333
    assert settings.ollama_timeout_sec == 180


def test_settings_env_ignores_invalid_ints(monkeypatch):
    settings = _load_settings(
        monkeypatch,
        {
            "APP_PORT": "30450",
            "BACKEND_PORT": "not-a-number",
            "OLLAMA_TIMEOUT_SECONDS": "invalid",
        },
    )

    assert settings.port == 30450
    assert settings.ollama_timeout_sec == 120
