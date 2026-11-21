@echo off
title Install ViGEmBus Driver
cd /d "%~dp0"
echo ========================================================
echo        DOWNLOADING OFFICIAL NEFARIUS VIGEMBUS DRIVER
echo ========================================================
echo.
powershell -Command "Invoke-WebRequest -Uri 'https://github.com/nefarius/ViGEmBus/releases/download/v1.22.0/ViGEmBus_1.22.0_x64_x86_arm64.exe' -OutFile 'ViGEmBus_Setup.exe'"
echo.
echo Launching installer...
start /wait ViGEmBus_Setup.exe
echo.
echo [DONE] ViGEmBus driver installed! You can now start the server with full Xbox 360 emulation.
pause
