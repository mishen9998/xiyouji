@echo off
setlocal enabledelayedexpansion
title Xiyouji Launcher

rem ===== Configuration =====
set "JAVA_HOME=C:\Users\20126\.jdks\openjdk-26.0.1"
set "MVN_CMD=D:\dpj\apache-maven-3.8.9\bin\mvn.cmd"
set "BACKEND_DIR=%~dp0backend"
set "PORT=8080"
set "MAX_WAIT=60"

echo ============================================
echo   Xiyouji Launcher
echo ============================================
echo.

rem ===== Step 1: Check if backend is already running (use netstat, no PowerShell) =====
echo [1/4] Checking backend on port %PORT% ...
netstat -an | findstr "LISTENING" | findstr ":%PORT% " >nul 2>&1
if !errorlevel!==0 (
    echo [OK] Backend is already running.
    goto :open_browser
)
echo [INFO] Backend not running. Will start a new one.

rem ===== Step 2: Verify dependencies =====
echo [2/4] Verifying dependencies ...
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [FAIL] Java not found: %JAVA_HOME%\bin\java.exe
    goto :error
)
if not exist "%MVN_CMD%" (
    echo [FAIL] Maven not found: %MVN_CMD%
    goto :error
)
echo [OK] Java and Maven located.

rem ===== Step 3: Start backend in a new persistent window =====
echo [3/4] Starting backend in a new window ...
set "PATH=%JAVA_HOME%\bin;%PATH%"

rem Use a here-doc style approach: write a tiny launcher bat in TEMP, then start it.
set "BACKEND_LAUNCHER=%TEMP%\xiyouji_backend_launcher.bat"
> "%BACKEND_LAUNCHER%" echo @echo off
>> "%BACKEND_LAUNCHER%" echo title Xiyouji Backend
>> "%BACKEND_LAUNCHER%" echo cd /d "%BACKEND_DIR%"
>> "%BACKEND_LAUNCHER%" echo set "PATH=%JAVA_HOME%\bin;%PATH%"
>> "%BACKEND_LAUNCHER%" echo echo Starting Spring Boot backend...
>> "%BACKEND_LAUNCHER%" echo echo.
>> "%BACKEND_LAUNCHER%" echo call "%MVN_CMD%" spring-boot:run
>> "%BACKEND_LAUNCHER%" echo echo.
>> "%BACKEND_LAUNCHER%" echo echo ============================================
>> "%BACKEND_LAUNCHER%" echo echo Backend has exited. Press any key to close.
>> "%BACKEND_LAUNCHER%" echo pause ^>nul

start "" "%BACKEND_LAUNCHER%"

rem ===== Step 4: Wait for backend to be ready =====
echo [4/4] Waiting for backend to be ready ...
set /a wait_count=0
:wait_loop
if !wait_count! geq %MAX_WAIT% goto :timeout
netstat -an | findstr "LISTENING" | findstr ":%PORT% " >nul 2>&1
if !errorlevel!==0 goto :ready
set /a wait_count+=1
echo     Waiting ... (!wait_count!/%MAX_WAIT%)
timeout /t 3 /nobreak >nul
goto :wait_loop

:ready
echo.
echo [SUCCESS] Backend is ready! (~!wait_count! x 3s)

:open_browser
echo.
echo Opening http://localhost:%PORT%/ ...
start "" "http://localhost:%PORT%/"
echo.
echo ============================================
echo   Game running at http://localhost:%PORT%/
echo   To stop the game, close the
echo   "Xiyouji Backend" window.
echo ============================================
echo.
echo This launcher window closes in 5 seconds ...
timeout /t 5 /nobreak >nul
exit /b 0

:timeout
echo.
echo [FAIL] Backend did not become ready within !MAX_WAIT! x 3s.
echo       Please check the "Xiyouji Backend" window for errors.

:error
echo.
echo ============================================
echo   Launch failed. See messages above.
echo ============================================
echo.
pause
exit /b 1
