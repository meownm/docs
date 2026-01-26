\
@echo off
title passport-demo-backend install
setlocal enabledelayedexpansion

cd /d %~dp0

if exist poetry.lock (
  echo Removing old lock to avoid stale constraints...
  del /q poetry.lock
)

echo Installing Poetry deps...
poetry install
if errorlevel 1 (
  echo ERROR: poetry install failed
  pause
  exit /b 1
)

echo OK
pause
