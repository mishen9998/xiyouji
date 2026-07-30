#!/bin/bash
# ========================================
# 西游记云服务器一键部署脚本
# 适用于宝塔面板 Linux 服务器
# 使用方法: bash deploy-cloud.sh
# ========================================
set -e

echo "============================================"
echo "  西游记云服务器部署脚本"
echo "============================================"
echo ""

# 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "[ERROR] Docker 未安装！请在宝塔面板软件商店安装 Docker管理器"
    exit 1
fi

if ! docker compose version &> /dev/null; then
    echo "[ERROR] Docker Compose 未安装！请安装 docker-compose-plugin"
    exit 1
fi

echo "[1/6] 检查环境... OK"
echo "  Docker: $(docker --version)"
echo "  Compose: $(docker compose version)"
echo ""

# 检查 JAR 是否存在
if [ ! -f "backend/target/xiyouji-roguelike-1.0.0.jar" ]; then
    echo "[ERROR] 未找到 backend/target/xiyouji-roguelike-1.0.0.jar"
    echo "  请先在本地执行 mvn clean package -DskipTests 构建 JAR"
    echo "  然后将 JAR 上传到服务器 backend/target/ 目录"
    exit 1
fi
echo "[2/6] 检查 JAR 文件... OK ($(du -h backend/target/xiyouji-roguelike-1.0.0.jar | cut -f1))"
echo ""

# 加载环境变量
if [ -f ".env.cloud" ]; then
    export $(cat .env.cloud | grep -v '^#' | xargs)
    echo "[3/6] 加载环境变量... OK"
else
    echo "[WARNING] 未找到 .env.cloud，使用默认配置（不安全！）"
fi
echo ""

# 停止旧容器
echo "[4/6] 停止旧容器..."
docker compose -f docker-compose-cloud.yml down 2>/dev/null || true
echo ""

# 构建并启动
echo "[5/6] 构建并启动容器（首次约3-5分钟）..."
docker compose -f docker-compose-cloud.yml up -d --build
echo ""

# 等待启动（2核2G服务器启动较慢，最多等待5分钟）
echo "[6/6] 等待应用启动（最多5分钟，请耐心等待）..."
MAX_WAIT=100  # 100次 × 3秒 = 300秒 = 5分钟
for i in $(seq 1 $MAX_WAIT); do
    HEALTH=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null)
    if [ "$HEALTH" = "200" ]; then
        echo ""
        echo "============================================"
        echo "  部署成功！"
        echo "============================================"
        echo ""
        echo "  访问地址: http://114.132.55.119:8080"
        echo "  健康检查: http://114.132.55.119:8080/actuator/health"
        echo ""
        echo "  容器状态:"
        docker compose -f docker-compose-cloud.yml ps
        echo ""
        echo "  查看日志: docker compose -f docker-compose-cloud.yml logs -f"
        echo "  停止服务: docker compose -f docker-compose-cloud.yml down"
        echo "============================================"
        exit 0
    fi
    # 每30次（约90秒）输出一次进度和容器状态
    if [ $((i % 30)) -eq 0 ]; then
        echo ""
        echo "  已等待 $((i * 3)) 秒，容器状态:"
        docker compose -f docker-compose-cloud.yml ps --format "table {{.Name}}\t{{.Status}}" 2>/dev/null || \
            docker compose -f docker-compose-cloud.yml ps
    fi
    echo -n "."
    sleep 3
done

echo ""
echo "[WARNING] 健康检查等待超时（5分钟），但容器可能仍在启动中"
echo "  请手动执行以下命令确认状态："
echo "    1. docker compose -f docker-compose-cloud.yml ps"
echo "    2. curl http://localhost:8080/actuator/health"
echo "    3. docker compose -f docker-compose-cloud.yml logs --tail=30 app"
echo ""
echo "  最近日志（最后30行）:"
docker compose -f docker-compose-cloud.yml logs --tail=30 app 2>/dev/null || \
    docker compose -f docker-compose-cloud.yml logs --tail=30
exit 0
