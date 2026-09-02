@echo off
title XiYouJi Roguelike Card Game
chcp 65001 >nul 2>&1

where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java 17 was not found in PATH.
    echo Install JDK 17 and configure JAVA_HOME/PATH first.
    pause
    exit /b 1
)

set "MVN_CMD=%~dp0mvnw.cmd"
echo ============================================
echo   XiYouJi Roguelike Card Game
echo ============================================
java -version
echo Maven Wrapper: %MVN_CMD%
echo.

REM ===== Change to Maven multi-module project root =====
cd /d "%~dp0"

REM ===== Build project if target does not exist =====
if not exist "xiyouji-bootstrap\target\classes" (
    echo [1/3] First run, compiling project...
        call "%MVN_CMD%" -pl xiyouji-bootstrap -am compile -q
    if errorlevel 1 (
        echo [ERROR] Compile failed! Check Maven and JDK config.
        pause
        exit /b 1
    )
    echo [1/3] Compile done.
    echo.
)

REM ===== Build frontend if needed =====
if exist "frontend-vue\src\App.vue" (
    if not exist "frontend-vue\dist\index.html" (
        echo [2/3] Building frontend...
        pushd "frontend-vue"
        call npm ci --silent
        if errorlevel 1 exit /b 1
        call npm run build --silent
        if errorlevel 1 exit /b 1
        popd
        echo [2/3] Frontend build done.
        echo.
    )
)

REM ===== Start Spring Boot with standalone profile =====
echo [3/3] Starting game server...
echo.
echo   =======================================
echo     Game URL: http://localhost:8080
echo     Press Ctrl+C to stop server
echo   =======================================
echo.

REM Open browser after 5 seconds
start "" /b cmd /c "timeout /t 5 /nobreak >nul && start http://localhost:8080"

REM Start with standalone profile (H2 in-memory DB, no MySQL/Redis needed)
call "%MVN_CMD%" -pl xiyouji-bootstrap -am package -DskipTests -q
if errorlevel 1 (
    echo [ERROR] Package failed!
    pause
    exit /b 1
)
java -jar xiyouji-bootstrap\target\xiyouji-bootstrap-1.0.0.jar --spring.profiles.active=standalone

pause
