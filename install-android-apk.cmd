@echo off
setlocal
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "APK=%~dp0app-android\build\outputs\apk\debug\app-android-debug.apk"

if not exist "%APK%" (
    echo [ERROR] APK not found at "%APK%". Run gradlew :app-android:assembleDebug first.
    exit /b 1
)

if not exist "%ADB%" (
    echo [ERROR] adb.exe not found at "%ADB%".
    exit /b 1
)

echo Checking connected Android devices via ADB...
"%ADB%" devices
echo.
echo Installing KNX Field Kit v3.10 to connected Android phone...
"%ADB%" install -r "%APK%"
if errorlevel 1 (
    echo.
    echo [HINT] Connect your Android phone via USB with USB Debugging enabled, or copy:
    echo        "%~dp0KNXFieldKit-android-v3.9.apk"
    echo        directly to your phone and tap to install!
    exit /b 1
)

echo Launching KNX Field Kit on Android device...
"%ADB%" shell am start -n dev.knxfieldkit.android/.MainActivity
echo Done!
