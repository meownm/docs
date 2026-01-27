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


class AppErrorLogIn(BaseModel):
    ts_utc: Optional[str] = Field(
        default=None,
        description="UTC timestamp (ISO-8601); если не передан, генерируется сервером",
    )
    platform: str = Field(..., min_length=1, description="Platform identifier (ios/android/web)")
    app_version: Optional[str] = Field(default=None, description="App version/build")
    error_message: str = Field(..., min_length=1, description="Error message")
    stacktrace: Optional[str] = Field(default=None, description="Stack trace")
    context_json: Optional[Dict[str, Any]] = Field(default=None, description="Additional context")
    user_agent: Optional[str] = Field(default=None, description="User-Agent")
    device_info: Optional[str] = Field(default=None, description="Device info")
    request_id: Optional[str] = Field(default=None, description="Correlation request id")
