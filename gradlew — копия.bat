@echo off
title install-android-sdk
setlocal enabledelayedexpansion

REM ===== CONFIG =====
set ANDROID_SDK_ROOT=C:\Android\sdk
set CMDLINE_TOOLS_ZIP=commandlinetools-win-11076708_latest.zip
set CMDLINE_TOOLS_URL=https://dl.google.com/android/repository/%CMDLINE_TOOLS_ZIP%

echo Android SDK root: %ANDROID_SDK_ROOT%
echo.

REM ===== CHECK JAVA =====
java -version >nul 2>&1
if errorlevel 1 (
  echo ERROR: Java not found. Install JDK 17 first.
  pause
  exit /b 1
)

REM ===== CREATE DIRS =====
if not exist %ANDROID_SDK_ROOT% (
  mkdir %ANDROID_SDK_ROOT%
)

cd /d %ANDROID_SDK_ROOT%

REM ===== DOWNLOAD CMDLINE TOOLS =====
if not exist %CMDLINE_TOOLS_ZIP% (
  echo Downloading Android cmdline-tools...
  powershell -Command "Invoke-WebRequest -Uri '%CMDLINE_TOOLS_URL%' -OutFile '%CMDLINE_TOOLS_ZIP%'"
  if errorlevel 1 (
    echo ERROR: Failed to download cmdline-tools
    pause
    exit /b 1
  )
)

REM ===== UNZIP =====
if not exist cmdline-tools\latest (
  echo Extracting cmdline-tools...
  powershell -Command "Expand-Archive -Force '%CMDLINE_TOOLS_ZIP%' '%ANDROID_SDK_ROOT%\cmdline-tools'"
  move cmdline-tools\cmdline-tools cmdline-tools\latest
)

REM ===== SET ENV VAR =====
echo Setting ANDROID_SDK_ROOT system variable...
setx ANDROID_SDK_ROOT "%ANDROID_SDK_ROOT%" >nul

REM ===== UPDATE PATH =====
setx PATH "%PATH%;%ANDROID_SDK_ROOT%\platform-tools;%ANDROID_SDK_ROOT%\cmdline-tools\latest\bin" >nul

REM ===== INSTALL PACKAGES =====
echo Installing SDK packages...
yes | sdkmanager ^
  "platform-tools" ^
  "platforms;android-34" ^
  "build-tools;34.0.0"

if errorlevel 1 (
  echo ERROR: sdkmanager failed
  pause
  exit /b 1
)

REM ===== LICENSES =====
yes | sdkmanager --licenses

echo.
echo Android SDK installation completed successfully.
echo Restart terminal before building APK.
pause
