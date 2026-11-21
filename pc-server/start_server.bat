@echo off
title ArachnidPad PC Server - Crimson Arachnid Edition
cd /d "%~dp0"
echo ========================================================
echo       ARACHNIDPAD PC COMPANION SERVER (WINDOWS)
echo ========================================================
echo.
python server.py
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Trying py launcher...
    py server.py
)
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Python not found or encountered an issue.
    echo Please install Python 3.8+ from https://www.python.org/downloads/
    pause
)
