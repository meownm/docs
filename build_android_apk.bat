title passport-demo build android apk
setlocal

set PROJECT_DIR=mobile_android_java
set GRADLEW=%PROJECT_DIR%\gradlew.bat
set APK_PATH=%PROJECT_DIR%\app\build\outputs\apk\debug

if not exist %PROJECT_DIR% (
  echo ERROR: mobile_android_java directory not found
  pause
  exit /b 1
)

if not exist %GRADLEW% (
  echo ERROR: gradlew.bat not found
  pause
  exit /b 1
)

cd /d %PROJECT_DIR%
call gradlew.bat clean || goto :err
call gradlew.bat assembleDebug || goto :err
cd /d ..

echo.
echo APK build completed.
echo Output folder:
echo %APK_PATH%
echo.
pause
exit /b 0

:err
echo ERROR: build failed
pause
exit /b 1