@echo off
setlocal enabledelayedexpansion
title Xiyouji Launcher

rem ===== Detect JDK =====
set "JAVA_HOME="

rem Method 1: Check JAVA_HOME env variable
if exist "%JAVA_HOME%\bin\java.exe" goto :java_found

rem Method 2: Check registry (Eclipse Adoptium)
for /f "tokens=2,*" %%a in ('reg query "HKLM\SOFTWARE\Eclipse Adoptium" /s /v "Path" 2^>nul ^| findstr /i "jdk-17"') do set "JAVA_HOME=%%b"
if exist "!JAVA_HOME!\bin\java.exe" goto :java_found

rem Method 3: Check common user paths
for /d %%i in ("%USERPROFILE%\.jdks\*") do if exist "%%i\bin\java.exe" set "JAVA_HOME=%%i"
if exist "!JAVA_HOME!\bin\java.exe" goto :java_found

rem Method 4: Check system paths
for /d %%i in ("C:\Program Files\Java\jdk*") do if exist "%%i\bin\java.exe" set "JAVA_HOME=%%i"
if exist "!JAVA_HOME!\bin\java.exe" goto :java_found

for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk*") do if exist "%%i\bin\java.exe" set "JAVA_HOME=%%i"
if exist "!JAVA_HOME!\bin\java.exe" goto :java_found

for /d %%i in ("%USERPROFILE%\AppData\Local\Programs\Eclipse Adoptium\jdk*") do if exist "%%i\bin\java.exe" set "JAVA_HOME=%%i"
if exist "!JAVA_HOME!\bin\java.exe" goto :java_found

rem Method 5: Try java on PATH as last resort
where java >nul 2>&1
if !errorlevel!==0 (
    for /f "delims=" %%i in ('where java 2^>nul') do set "JAVA_BIN=%%i"
    if defined JAVA_BIN (
        set "JAVA_HOME=!JAVA_BIN:\bin\java.exe=!"
        if exist "!JAVA_HOME!\bin\java.exe" goto :java_found
    )
)

echo [FAIL] JDK not found! Please install JDK 17+.
echo   Download: https://adoptium.net/download/
goto :error

:java_found
echo [OK] JDK found: !JAVA_HOME!

rem ===== Detect Maven =====
set "MVN_HOME="
set "MVN_CMD=mvn"

rem Check if project has mvnw (Maven wrapper) — best option
if exist "%~dp0mvnw.cmd" (
    set "MVN_CMD=%~dp0mvnw.cmd"
    echo [OK] Using Maven Wrapper ^(mvnw^)
    goto :mvn_found
)
if exist "%~dp0mvnw" (
    echo [OK] Using Maven Wrapper ^(mvnw^)
    goto :mvn_found
)

rem Try MAVEN_HOME env variable
if exist "%MAVEN_HOME%\bin\mvn.cmd" (
    set "MVN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
    goto :mvn_found
)

rem Try common paths
for /d %%i in ("D:\dpj\apache-maven-*") do if exist "%%i\bin\mvn.cmd" (
    set "MVN_CMD=%%i\bin\mvn.cmd"
    goto :mvn_found
)
for /d %%i in ("C:\Program Files\apache-maven-*") do if exist "%%i\bin\mvn.cmd" (
    set "MVN_CMD=%%i\bin\mvn.cmd"
    goto :mvn_found
)

rem Try user profile (new install location - preferred over PATH which may have broken mvn)
for /d %%i in ("%USERPROFILE%\apache-maven-*") do if exist "%%i\bin\mvn.cmd" (
    set "MVN_CMD=%%i\bin\mvn.cmd"
    echo [OK] Using Maven at %%i
    goto :mvn_found
)

rem Try PATH
where mvn >nul 2>&1
if !errorlevel!==0 (
    echo [OK] Maven found on PATH
    goto :mvn_found
)

echo [FAIL] Maven not found! Please install Maven or run from project root with mvnw.
echo   Download: https://maven.apache.org/download.cgi
goto :error

:mvn_found
set "BACKEND_DIR=%~dp0"
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
echo [OK] Java and Maven located.

rem ===== Step 3: Start backend in a new persistent window =====
echo [3/4] Starting backend in a new window ...
set "PATH=%JAVA_HOME%\bin;%PATH%"

rem Write a tiny launcher bat in TEMP, then start it.
set "BACKEND_LAUNCHER=%TEMP%\xiyouji_backend_launcher.bat"
> "%BACKEND_LAUNCHER%" echo @echo off
>> "%BACKEND_LAUNCHER%" echo title Xiyouji Backend
>> "%BACKEND_LAUNCHER%" echo cd /d "%BACKEND_DIR%"
>> "%BACKEND_LAUNCHER%" echo set "PATH=%JAVA_HOME%\bin;%PATH%"
>> "%BACKEND_LAUNCHER%" echo echo Starting Spring Boot backend...
>> "%BACKEND_LAUNCHER%" echo echo.
>> "%BACKEND_LAUNCHER%" echo call "%MVN_CMD%" -pl xiyouji-bootstrap -am package -DskipTests -q
>> "%BACKEND_LAUNCHER%" echo java -jar xiyouji-bootstrap\target\xiyouji-bootstrap-1.0.0.jar --spring.profiles.active=standalone
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
