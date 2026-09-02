@echo off
setlocal
title XiYouJi - Distributed Compose Cluster
chcp 65001 >nul 2>&1
cd /d "%~dp0"

where docker >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker was not found in PATH. Install and start Docker Desktop first.
    pause
    exit /b 1
)

docker compose version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker Compose v2 is unavailable.
    pause
    exit /b 1
)

if not exist ".env" (
    echo [ERROR] Missing .env file.
    echo Copy .env.example to .env and replace all placeholder secrets first.
    pause
    exit /b 1
)

echo ============================================
echo   XiYouJi - Distributed Compose Cluster
echo ============================================
echo.
echo [1/2] Validating Compose configuration...
docker compose config >nul
if errorlevel 1 (
    echo [ERROR] Compose configuration is invalid. Check .env values.
    pause
    exit /b 1
)

echo [2/2] Building and starting the two application instances...
docker compose up -d --build --wait
if errorlevel 1 (
    echo [ERROR] Cluster startup failed. Run: docker compose logs --tail=100
    pause
    exit /b 1
)

echo.
docker compose ps
echo.
echo [OK] Game:       http://localhost:8080
echo [OK] Health:     http://localhost:8080/actuator/health
echo [INFO] Stop with: docker compose down
pause
