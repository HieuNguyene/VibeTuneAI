@echo off
title MongoDB Local - Docker Runner
echo ====================================================================
echo   KHOI DONG MONGODB LOCAL (PORT 27017)
echo ====================================================================
echo.

docker compose up -d
if %ERRORLEVEL% EQU 0 (
    echo.
    echo [*] MongoDB Local dang chay tai port 27017!
    echo [*] Database: music_app
    echo [*] URI: mongodb://127.0.0.1:27017/music_app
    echo.
) else (
    echo.
    echo [!] Chua khoi dong duoc Docker. Vui long bat Docker Desktop truoc!
)
pause
