@echo off
title XiYouJi - Distributed Cluster
chcp 65001 >nul 2>&1

REM ===== Detect JDK 17 =====
set "JAVA_HOME="
if exist "C:\Users\20126\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.19.10-hotspot" (
    set "JAVA_HOME=C:\Users\20126\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
)
if not defined JAVA_HOME (
    for /d %%i in ("C:\Program Files\Java\jdk-17*") do set "JAVA_HOME=%%i"
)
if not defined JAVA_HOME (
    for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do set "JAVA_HOME=%%i"
)
if not defined JAVA_HOME (
    echo [ERROR] JDK 17 not found!
    pause
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
set "BASE_DIR=%~dp0"
cd /d "%BASE_DIR%backend"

echo ============================================
echo   XiYouJi - Distributed Cluster Startup
echo ============================================
echo.

REM ===== Build JAR if not exists =====
set "JAR_FILE=target\xiyouji-roguelike-1.0.0.jar"
if not exist "%JAR_FILE%" (
    echo [1/3] Building JAR package...
    call mvn clean package -DskipTests -q
    if errorlevel 1 (
        echo [ERROR] Build failed!
        pause
        exit /b 1
    )
    echo [OK] Build complete.
) else (
    echo [1/3] JAR package found, skip build.
)

REM ===== Check MySQL =====
echo [2/3] Checking MySQL...
mysql -u root -p123456 -e "SELECT 1" xiyouji >nul 2>&1
if errorlevel 1 (
    echo [WARN] MySQL not ready. Start MySQL80 service first.
    echo        net start MySQL80
) else (
    echo [OK] MySQL connected.
)

REM ===== Check Redis =====
echo [3/3] Checking Redis...
redis-cli ping >nul 2>&1
if errorlevel 1 (
    echo [WARN] Redis not ready. Start Redis first.
) else (
    echo [OK] Redis connected.
)

echo.
echo =======================================
echo   Cluster Architecture:
echo.
echo   Client -^> Nginx (:80) -^> Load Balancer
echo                         +-= Instance 1 (:8081)
echo                         +-= Instance 2 (:8082)
echo.
echo   Shared Storage:
echo     MySQL (:3306) - Database
echo     Redis  (:6379) - Session/Cache
echo.
echo   Monitoring:
echo     Prometheus (:9090) - Metrics
echo     Grafana     (:3000) - Dashboard
echo.
echo   Verify instances:
echo     curl http://localhost:8081/api/instance/info
echo     curl http://localhost:8082/api/instance/info
echo =======================================
echo.

REM ===== Start Instance 1 =====
echo Starting Instance 1 (port 8081)...
start "XiYouJi-Instance-1" cmd /c "cd /d "%BASE_DIR%backend" && java -jar %JAR_FILE% --spring.profiles.active=instance1,distributed"

REM ===== Wait for Instance 1 =====
timeout /t 15 /nobreak >nul

REM ===== Start Instance 2 =====
echo Starting Instance 2 (port 8082)...
start "XiYouJi-Instance-2" cmd /c "cd /d "%BASE_DIR%backend" && java -jar %JAR_FILE% --spring.profiles.active=instance2,distributed"

echo.
echo =======================================
echo   Two instances started in new windows!
echo.
echo   Instance 1: http://localhost:8081
echo   Instance 2: http://localhost:8082
echo.
echo   Nginx Load Balancer (optional):
echo     1. Install Nginx
echo     2. Copy nginx\nginx.conf to Nginx config
echo     3. Start Nginx, visit http://localhost
echo.
echo   Docker full deployment:
echo     docker-compose up -d
echo.
echo   To stop: close the instance windows
echo =======================================
echo.
echo   Press any key to close this window
echo   (instances will keep running)
pause >nul
