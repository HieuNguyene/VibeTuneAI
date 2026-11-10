@echo off
title Vibe Music - Backend Server
echo ====================================================================
echo   VIBE MUSIC - BACKEND NODE.JS + MONGODB SERVER
echo ====================================================================
echo.

cd /d "%~dp0backend"

echo [*] Kiem tra port 5000...
for /f "tokens=5" %%a in ('netstat -aon 2^>nul ^| findstr ":5000" ^| findstr "LISTENING"') do (
    echo [!] Giai phong port 5000 tu tien trinh PID %%a...
    taskkill /f /pid %%a >nul 2>&1
)

echo [*] Dang khoi dong Backend Server...
echo [*] Android Emulator API: http://10.0.2.2:5000/api/v1
echo [*] Browser Healthcheck:  http://localhost:5000/api/v1/health
echo ====================================================================
echo.

node --watch server.js
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [LOI] Backend dung voi ma loi %ERRORLEVEL%.
)
pause
