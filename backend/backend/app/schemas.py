from typing import Any, Dict, Optional
from pydantic import BaseModel, Field


class ErrorInfo(BaseModel):
    error_code: str = Field(..., description="Stable error code")
    message: str


class MRZKeys(BaseModel):
    document_number: str
    date_of_birth: str  # YYYY-MM-DD
    date_of_expiry: str  # YYYY-MM-DD


class RecognizeResponse(BaseModel):
    request_id: str
    mrz: Optional[MRZKeys] = None
    raw: Optional[Dict[str, Any]] = None
    error: Optional[ErrorInfo] = None


class NFCPayload(BaseModel):
    # passport parameters extracted from NFC (DG1 etc.); for demo keep generic
    passport: Dict[str, Any]
    face_image_b64: str


class NFCStoreResponse(BaseModel):
    scan_id: str
    status: str = "stored"
