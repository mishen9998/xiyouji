@echo off
title SonarQube Analysis
echo ============================================
echo   SonarQube 静态代码分析
echo ============================================
echo.
echo 前提条件:
echo   1. SonarQube 服务已启动 (docker-compose up -d sonarqube)
echo   2. 访问 http://localhost:9000 获取 Token
echo   3. 在 SonarQube 中创建项目，获取 analysis token
echo.

set /p SONAR_TOKEN="请输入 SonarQube Token: "

set JAVA_HOME=C:\Users\20126\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.19.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%

cd /d "%~dp0backend"

echo.
echo 正在运行 SonarQube 分析...
echo ============================================

call mvn sonar:sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.login=%SONAR_TOKEN%

echo.
echo ============================================
echo 分析完成！请访问 http://localhost:9000 查看结果
echo ============================================
pause
