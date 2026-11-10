@echo off
title Vibe Music - Thu Muc Nhac Cua Ban
echo ====================================================================
echo   DANG MO THU MUC CHUA FILE NHAC MP3 VA ANH BIA TRUC TIEP TREN MAY
echo ====================================================================
echo.
echo [*] Tha file .mp3 cua ban vao thu muc: audios
echo [*] Tha anh bia .jpg / .png cua ban vao thu muc: covers
echo.
start  explorer  %~dp0backend\public\uploads
