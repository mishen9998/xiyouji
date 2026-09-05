@echo off
setlocal
cd /d "%~dp0"
echo Rebuilding and starting the latest game code. This may take several minutes.
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\demo.ps1" up
if errorlevel 1 (
  echo.
  echo Rebuild failed. Read the error shown above.
  pause
  exit /b 1
)
echo.
echo The latest game build is ready at http://localhost:8080
pause
