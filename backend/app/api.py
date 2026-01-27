from fastapi import APIRouter

router = APIRouter()

@router.post("/process")
def process(payload: dict):
    return {"result": "ok"}
