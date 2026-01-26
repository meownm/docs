@echo off
title passport-demo install gradlew
setlocal

set PROJECT_DIR=mobile_android_java

if not exist %PROJECT_DIR% (
  echo ERROR: mobile_android_java directory not found
  pause
  exit /b 1
)

cd /d %PROJECT_DIR%

where gradle >nul 2>&1
if errorlevel 1 (
  echo ERROR: gradle not found in PATH.
  echo Install Android Studio or standalone Gradle.
  pause
  exit /b 1
)

if exist gradlew (
  echo gradlew already exists.
  pause
  exit /b 0
)

echo Generating Gradle Wrapper...
gradle wrapper --gradle-version 8.6
if errorlevel 1 (
  echo ERROR: gradle wrapper generation failed
  pause
  exit /b 1
)

echo gradlew successfully generated.
pause