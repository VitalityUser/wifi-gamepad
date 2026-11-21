@echo off
title WiFi Gamepad PC Server
cd /d "%~dp0"
echo ========================================================
echo          STARTING WIFI GAMEPAD PC SERVER
echo ========================================================
echo.
py -3.14 server.py
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Trying python fallback...
    python server.py
)
pause
