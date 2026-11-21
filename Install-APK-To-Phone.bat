@echo off
title Install WiFi Gamepad to Phone
cd /d "%~dp0"
echo ========================================================
echo        INSTALLING WIFI GAMEPAD APK TO PHONE
echo ========================================================
echo.
adb devices
echo.
echo Installing bin\WiFiGamepad.apk...
adb install -r bin\WiFiGamepad.apk
if %ERRORLEVEL% EQU 0 (
    echo.
    echo Launching app on phone...
    adb shell am start -n com.antigravity.wifigamepad/.MainActivity
    echo.
    echo [SUCCESS] App installed and launched!
) else (
    echo.
    echo [NOTE] If no device was found, please transfer the APK directly:
    echo        File location: bin\WiFiGamepad.apk
    echo        Send it to your phone via USB cable, WhatsApp, Telegram, or Google Drive.
)
echo.
pause
