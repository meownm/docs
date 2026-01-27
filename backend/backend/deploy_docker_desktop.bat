\
@echo off
title passport-demo-backend deploy_docker_desktop
setlocal enabledelayedexpansion
cd /d %~dp0

if not exist .env (
  echo ERROR: .env not found. Create it from .env.example first.
  pause
  exit /b 1
)

for /f "usebackq tokens=1,2 delims==" %%a in (`type .env ^| findstr /v /r "^\s*#"` ) do (
  if not "%%a"=="" set "%%a=%%b"
)

echo Building docker image...
docker build -t passport-demo-backend:latest .
if errorlevel 1 (
  echo ERROR: docker build failed
  pause
  exit /b 1
)

echo Running container...
docker rm -f passport-demo-backend >nul 2>&1

docker run -d ^
  --name passport-demo-backend ^
  -p %BACKEND_PORT%:8000 ^
  -e OLLAMA_BASE_URL=%OLLAMA_BASE_URL% ^
  -e OLLAMA_MODEL=%OLLAMA_MODEL% ^
  -e OLLAMA_TIMEOUT_SECONDS=%OLLAMA_TIMEOUT_SECONDS% ^
  -e DATA_DIR=/data ^
  -e DB_PATH=/data/app.db ^
  -e FILES_DIR=/data/files ^
  -v "%cd%\data":/data ^
  passport-demo-backend:latest

if errorlevel 1 (
  echo ERROR: docker run failed
  pause
  exit /b 1
)

echo Swagger: http://localhost:%BACKEND_PORT%/docs
echo Web UI: http://localhost:%BACKEND_PORT%/
pause
