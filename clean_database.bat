@echo off
title Vibe Music - Xoa Sach Du Lieu Ao
echo ====================================================================
echo   XOA SACH TOAN BO DU LIEU AO TRONG MONGODB LOCAL
echo ====================================================================
echo.

cd /d  %~dp0backend
node src/seeder/clearDatabase.js

echo.
pause
