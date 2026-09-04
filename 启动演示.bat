@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\demo.ps1" up
if errorlevel 1 (
  echo.
  echo Demo startup failed. See the error above.
  pause
  exit /b 1
)
echo.
echo Demo is running at http://localhost:8080
pause
