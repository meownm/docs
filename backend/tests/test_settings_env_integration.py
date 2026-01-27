import importlib


ENV_KEYS = [
    "APP_HOST",
    "APP_PORT",
    "BACKEND_HOST",
    "BACKEND_PORT",
    "OLLAMA_TIMEOUT_SEC",
    "OLLAMA_TIMEOUT_SECONDS",
]


def _reload_modules(monkeypatch, env):
    import app.settings as settings_module
    import app.api as api_module
    import app.db as db_module

    for key in ENV_KEYS:
        monkeypatch.delenv(key, raising=False)
    for key, value in env.items():
        monkeypatch.setenv(key, value)
    importlib.reload(settings_module)
    importlib.reload(api_module)
    importlib.reload(db_module)
    return settings_module, api_module, db_module


def test_env_settings_propagate_to_modules(monkeypatch):
    settings_module, api_module, db_module = _reload_modules(
        monkeypatch,
        {
            "BACKEND_HOST": "0.0.0.0",
            "BACKEND_PORT": "5678",
            "OLLAMA_TIMEOUT_SECONDS": "240",
        },
    )

    assert settings_module.settings.host == "0.0.0.0"
    assert settings_module.settings.port == 5678
    assert settings_module.settings.ollama_timeout_sec == 240
    assert api_module.settings.host == "0.0.0.0"
    assert db_module.settings.port == 5678


def test_env_settings_fall_back_for_modules(monkeypatch):
    settings_module, api_module, db_module = _reload_modules(
        monkeypatch,
        {
            "APP_HOST": "10.10.0.1",
            "APP_PORT": "9000",
            "BACKEND_HOST": "",
            "BACKEND_PORT": "",
            "OLLAMA_TIMEOUT_SEC": "180",
            "OLLAMA_TIMEOUT_SECONDS": "",
        },
    )

    assert settings_module.settings.host == "10.10.0.1"
    assert settings_module.settings.port == 9000
    assert settings_module.settings.ollama_timeout_sec == 180
    assert api_module.settings.host == "10.10.0.1"
    assert db_module.settings.port == 9000
