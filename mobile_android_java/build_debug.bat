@echo off
setlocal

echo ==============================
echo Building eMRTDReader (DEBUG)
echo ==============================

call gradlew clean
if errorlevel 1 (
    echo.
    echo !!! CLEAN FAILED !!!
    pause
    exit /b 1
)

call gradlew :sdk:compileDebugJavaWithJavac
if errorlevel 1 (
    echo.
    echo !!! SDK BUILD FAILED !!!
    pause
    exit /b 1
)

call gradlew :app:assembleDebug
if errorlevel 1 (
    echo.
    echo !!! APP BUILD FAILED !!!
    pause
    exit /b 1
)

echo.
echo ==============================
echo BUILD SUCCESS
echo ==============================
pause
endlocal
