\
@echo off
title passport-demo-backend run_dev
setlocal enabledelayedexpansion
cd /d %~dp0

if not exist .env (
  echo Creating .env from .env.example...
  copy /y .env.example .env >nul
)

for /f "usebackq tokens=1,2 delims==" %%a in (`type .env ^| findstr /v /r "^\s*#"` ) do (
  if not "%%a"=="" set "%%a=%%b"
)

echo Swagger will be available at: http://%BACKEND_HOST%:%BACKEND_PORT%/docs
echo Web UI will be available at: http://%BACKEND_HOST%:%BACKEND_PORT%/

poetry run uvicorn app.main:app --host %BACKEND_HOST% --port %BACKEND_PORT% --reload
if errorlevel 1 (
  echo ERROR: backend failed to start
  pause
  exit /b 1
)

pause
