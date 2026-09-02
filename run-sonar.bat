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

cd /d "%~dp0"

echo.
echo 正在运行 SonarQube 分析...
echo ============================================

call mvnw.cmd -pl xiyouji-bootstrap -am sonar:sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.login=%SONAR_TOKEN%

echo.
echo ============================================
echo 分析完成！请访问 http://localhost:9000 查看结果
echo ============================================
pause
