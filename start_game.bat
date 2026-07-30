@echo off
title XiYouJi Roguelike Card Game
chcp 65001 >nul 2>&1

REM ===== Detect JDK 17 =====
set "JAVA_HOME="
for /f "tokens=2,*" %%a in ('reg query "HKLM\SOFTWARE\Eclipse Adoptium" /s /v "Path" 2^>nul ^| findstr /i "jdk-17"') do set "JAVA_HOME=%%b"
if not defined JAVA_HOME (
    if exist "C:\Users\20126\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.19.10-hotspot" (
        set "JAVA_HOME=C:\Users\20126\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
    )
)
if not defined JAVA_HOME (
    for /d %%i in ("C:\Program Files\Java\jdk-17*") do set "JAVA_HOME=%%i"
)
if not defined JAVA_HOME (
    for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do set "JAVA_HOME=%%i"
)
if not defined JAVA_HOME (
    echo [ERROR] JDK 17 not found! Please install Eclipse Temurin JDK 17.
    echo Download: https://adoptium.net/temurin/releases/?version=17
    pause
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
echo ============================================
echo   XiYouJi Roguelike Card Game
echo ============================================
echo JDK: %JAVA_HOME%
echo.

REM ===== Change to backend directory =====
cd /d "%~dp0backend"

REM ===== Build project if target does not exist =====
if not exist "target\classes" (
    echo [1/3] First run, compiling project...
    call mvn compile -q
    if errorlevel 1 (
        echo [ERROR] Compile failed! Check Maven and JDK config.
        pause
        exit /b 1
    )
    echo [1/3] Compile done.
    echo.
)

REM ===== Build frontend if needed =====
if exist "..\frontend-vue\src\App.vue" (
    if not exist "src\main\resources\static\js\index.js" (
        echo [2/3] Building frontend...
        pushd "..\frontend-vue"
        call npm install --silent 2>nul
        call npx vite build --silent 2>nul
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
call mvn spring-boot:run -Dspring-boot.run.profiles=standalone

pause
