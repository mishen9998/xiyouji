@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\demo.ps1" down
if errorlevel 1 (
  echo.
  echo Demo shutdown failed. See the error above.
  pause
  exit /b 1
)
pause
