@echo off
setlocal

set GRADLE_CMD=
if exist "gradlew.bat" (
  set GRADLE_CMD=gradlew.bat
) else if exist "gradle.bat" (
  set GRADLE_CMD=gradle.bat
) else (
  set GRADLE_CMD=gradle
)

call %GRADLE_CMD% assembleDebug
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

echo APK output: app\build\outputs\apk\debug\app-debug.apk
endlocal
