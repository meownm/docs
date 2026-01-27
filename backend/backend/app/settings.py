from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    backend_host: str = "127.0.0.1"
    backend_port: int = 30450

    ollama_base_url: str = "http://127.0.0.1:11434"
    ollama_model: str = "qwen3-vl:30b"
    ollama_timeout_seconds: int = 120

    data_dir: str = "./data"
    db_path: str = "./data/app.db"
    files_dir: str = "./data/files"

    llm_lang: str = "ru"

    class Config:
        env_file = ".env"
        extra = "ignore"


settings = Settings()
