@echo off
title passport-demo build android apk
setlocal

set PROJECT_DIR=mobile_android_java
set GRADLEW=\gradlew
set APK_PATH=%PROJECT_DIR%\app\build\outputs\apk\debug

if not exist %PROJECT_DIR% (
  echo ERROR: mobile_android_java directory not found
  pause
  exit /b 1
)

if not exist %GRADLEW% (
  echo ERROR: gradlew not found.
  echo Open mobile_android_java once in Android Studio to generate Gradle Wrapper.
  pause
  exit /b 1
)

cd /d %PROJECT_DIR%

call gradlew clean
if errorlevel 1 (
  echo ERROR: gradle clean failed
  pause
  exit /b 1
)

call gradlew assembleDebug
if errorlevel 1 (
  echo ERROR: APK build failed
  pause
  exit /b 1
)

cd /d ..

echo.
echo APK build completed.
echo Output folder:
echo %APK_PATH%
echo.
pause