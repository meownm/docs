from fastapi import APIRouter

router = APIRouter()

@router.post("/process")
def process(payload: dict):
    """
    Минимальный endpoint для проверки wiring'а.
    Контракт будет формализован отдельно.
    """
    return {
        "result": "ok"
    }
