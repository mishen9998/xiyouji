@echo off
title 西游记后端服务
setlocal enabledelayedexpansion

REM ===== 配置区 =====
set "JAVA_HOME=C:\Users\20126\.jdks\openjdk-26.0.1"
set "MAVEN_CMD=D:\dpj\apache-maven-3.8.9\bin\mvn.cmd"
set "BACKEND_DIR=%~dp0backend"
if "!BACKEND_DIR:~-1!"=="\" set "BACKEND_DIR=!BACKEND_DIR:~0,-1!"

echo ============================================
echo   西游记 - Spring Boot 后端服务
echo ============================================
echo.
echo  后端目录: !BACKEND_DIR!
echo  Java:     !JAVA_HOME!
echo  Maven:    !MAVEN_CMD!
echo.
echo  关闭此窗口将停止后端服务
echo  首次启动需编译，请耐心等待 30-60 秒
echo.

cd /d "!BACKEND_DIR!"
if errorlevel 1 (
    echo [错误] 无法进入后端目录: !BACKEND_DIR!
    pause
    exit /b 1
)

set "PATH=!JAVA_HOME!\bin;%PATH%"

echo [验证] Java 版本:
"!JAVA_HOME!\bin\java.exe" -version
echo.

echo [启动] 正在运行 mvn spring-boot:run ...
echo.
"!MAVEN_CMD!" spring-boot:run

echo.
echo ============================================
echo  后端服务已停止
echo  如出现错误信息，请截图保存后联系开发者
echo ============================================
pause
exit /b %errorlevel%
