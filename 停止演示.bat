@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\demo.ps1" down
if errorlevel 1 (
  echo.
  echo Game shutdown failed. Read the error shown above.
  pause
  exit /b 1
)
echo Game services stopped. Saved database volumes were preserved.
pause
