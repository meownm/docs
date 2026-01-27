from fastapi import FastAPI
from backend.app.api import router

app = FastAPI(title="FastAPI", version="0.1.0")
app.include_router(router, prefix="/api")
