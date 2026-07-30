@echo off
chcp 65001 >nul 2>&1
title XiYouJi Docker Manager

:menu
cls
echo ============================================
echo    XiYouJi Roguelike - Docker Manager
echo ============================================
echo.
echo  [1] Start all services    (docker-compose up -d --build)
echo  [2] Stop all services     (docker-compose down)
echo  [3] Restart all services  (down + up)
echo  [4] View service status   (docker ps)
echo  [5] View logs             (docker-compose logs -f)
echo  [6] View app-1 logs       (docker logs xiyouji-app-1 -f)
echo  [7] View app-2 logs       (docker logs xiyouji-app-2 -f)
echo  [8] Clean rebuild         (down + prune + build)
echo  [9] Exit
echo.
set /p choice=Enter choice: 

if "%choice%"=="1" goto start
if "%choice%"=="2" goto stop
if "%choice%"=="3" goto restart
if "%choice%"=="4" goto status
if "%choice%"=="5" goto logs
if "%choice%"=="6" goto logs1
if "%choice%"=="7" goto logs2
if "%choice%"=="8" goto clean
if "%choice%"=="9" exit
goto menu

:start
echo Starting all services...
set DOCKER_BUILDKIT=0
docker-compose -p xiyouji up -d --build
echo.
echo Done! Services starting...
pause
goto menu

:stop
echo Stopping all services...
docker-compose -p xiyouji down
echo Done!
pause
goto menu

:restart
echo Restarting all services...
docker-compose -p xiyouji down
set DOCKER_BUILDKIT=0
docker-compose -p xiyouji up -d --build
echo Done!
pause
goto menu

:status
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
echo.
pause
goto menu

:logs
docker-compose -p xiyouji logs -f --tail=50
goto menu

:logs1
docker logs xiyouji-app-1 -f --tail=50
goto menu

:logs2
docker logs xiyouji-app-2 -f --tail=50
goto menu

:clean
echo WARNING: This will remove all containers, images, and volumes!
set /p confirm=Are you sure? (y/n): 
if /i not "%confirm%"=="y" goto menu
docker-compose -p xiyouji down -v
docker system prune -f
set DOCKER_BUILDKIT=0
docker-compose -p xiyouji up -d --build
echo Done! Fresh build complete.
pause
goto menu
