@echo off
title Stop XiYouJi Game Server
chcp 65001 >nul 2>&1

echo Stopping game server...

REM Find and kill Java process listening on port 8080
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do (
    echo Stopping process PID: %%a
    taskkill /f /pid %%a 2>nul
)

echo.
echo Game server stopped.
timeout /t 2 /nobreak >nul
