@echo off
chcp 65001 >nul
title 西游记联机服务 (standalone模式 - 无需MySQL/Redis)

REM ====== 环境配置 ======
set JAVA_HOME=C:\Users\20126\.jdks\ms-17.0.19
set MAVEN_HOME=D:\dpj\apache-maven-3.8.9
set Path=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%Path%

REM ====== 工作目录 ======
cd /d "c:\Users\20126\Desktop\西游记\backend"

echo ============================================
echo   西游记联机服务启动中...
echo   模式: standalone (H2内存数据库, 无外部依赖)
echo   端口: 8080
echo ============================================
echo.

set SPRING_PROFILES_ACTIVE=standalone
call "%MAVEN_HOME%\bin\mvn.cmd" spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx768m"

echo.
echo 服务已停止。按任意键退出...
pause >nul
