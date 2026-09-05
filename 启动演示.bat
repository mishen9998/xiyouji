@echo off
setlocal
cd /d "%~dp0"
echo Starting Journey to the West. Keep this window open.
echo The first launch may take several minutes; later launches are faster.
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\demo.ps1" up
if errorlevel 1 (
  echo.
  echo Game startup failed. Read the error shown above.
  echo Common causes: Docker Desktop is not ready, port 8080 is busy, or image download failed.
  pause
  exit /b 1
)
echo.
echo Game is ready at http://localhost:8080
echo Closing this window will not stop the game service.
pause
