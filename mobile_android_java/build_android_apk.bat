setlocal enabledelayedexpansion

title build_android_apk

echo ===============================
echo Building Android APK (DEBUG)
echo ===============================

REM Переходим в директорию скрипта
cd /d %~dp0

REM Проверка gradlew
if not exist gradlew.bat (
    echo ERROR: gradlew.bat not found
    pause
    exit /b 1
)

REM Очистка предыдущих билдов
call gradlew.bat clean
if errorlevel 1 (
    echo ERROR: gradle clean failed
    pause
    exit /b 1
)

REM Сборка debug APK
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo ERROR: assembleDebug failed
    pause
    exit /b 1
)

REM Проверка результата
set APK_PATH=app\build\outputs\apk\debug\app-debug.apk

if exist "%APK_PATH%" (
    echo ===============================
    echo BUILD SUCCESS
    echo APK:
    echo %APK_PATH%
    echo ===============================
) else (
    echo ===============================
    echo ERROR: APK not found
    echo Expected at:
    echo %APK_PATH%
    echo ===============================
)

pause
