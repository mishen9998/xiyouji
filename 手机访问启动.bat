@echo off
title 西游记 - 手机访问模式
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
    echo [ERROR] JDK 17 not found!
    pause
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"

REM ===== 获取本机局域网 IP =====
echo ============================================
echo   西游记 - 手机访问模式
echo ============================================
echo.
echo 正在获取本机 IP 地址...
echo.

for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /i "IPv4"') do (
    set "LOCAL_IP=%%a"
)
REM 去除空格
set "LOCAL_IP=%LOCAL_IP: =%"

if not defined LOCAL_IP (
    echo [WARNING] 无法自动获取IP，请手动运行 ipconfig 查看
    set "LOCAL_IP=你的电脑IP"
)

echo ============================================
echo   电脑本机 IP: %LOCAL_IP%
echo.
echo   在手机浏览器中打开以下地址:
echo.
echo   http://%LOCAL_IP%:8080
echo.
echo   (手机和电脑必须连接同一个WiFi)
echo ============================================
echo.

REM ===== 放行防火墙 8080 端口 =====
echo 正在放行防火墙端口 8080...
netsh advfirewall firewall add rule name="XiYouJi-8080" dir=in action=allow protocol=TCP localport=8080 >nul 2>&1
echo 防火墙已放行。
echo.

REM ===== 启动游戏服务器 =====
cd /d "%~dp0backend"

if not exist "target\classes" (
    echo 首次运行，正在编译项目...
    call mvn compile -q
    if errorlevel 1 (
        echo [ERROR] 编译失败！
        pause
        exit /b 1
    )
)

echo 正在启动游戏服务器...
echo 服务器启动后，用手机扫描上方地址即可游玩
echo 按 Ctrl+C 可停止服务器
echo.

REM 同时在电脑浏览器打开
start "" /b cmd /c "timeout /t 5 /nobreak >nul && start http://localhost:8080"

call mvn spring-boot:run -Dspring-boot.run.profiles=standalone

REM 服务器停止后移除防火墙规则
netsh advfirewall firewall delete rule name="XiYouJi-8080" >nul 2>&1

pause
