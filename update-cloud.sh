#!/bin/bash
# ========================================
# 西游记云服务器 - 增量更新脚本
# 仅更新应用JAR，不重新拉取MySQL/Redis镜像
# 使用方法: bash update-cloud.sh
# ========================================
set -e

echo "============================================"
echo "  西游记应用增量更新"
echo "============================================"
echo ""

# 检查 JAR 是否存在
if [ ! -f "backend/target/xiyouji-roguelike-1.0.0.jar" ]; then
    echo "[ERROR] 未找到 backend/target/xiyouji-roguelike-1.0.0.jar"
    echo "  请将新构建的JAR上传到 backend/target/ 目录"
    exit 1
fi

JAR_SIZE=$(du -h backend/target/xiyouji-roguelike-1.0.0.jar | cut -f1)
echo "[1/4] 检查新JAR文件... OK ($JAR_SIZE)"
echo ""

# 只重新构建并重启 app 容器（不影响 MySQL 和 Redis）
echo "[2/4] 重新构建应用镜像..."
docker compose -f docker-compose-cloud.yml build app
echo ""

echo "[3/4] 重启应用容器..."
docker compose -f docker-compose-cloud.yml up -d app
echo ""

# 等待启动（最多5分钟）
echo "[4/4] 等待应用启动（最多5分钟）..."
MAX_WAIT=100
for i in $(seq 1 $MAX_WAIT); do
    HEALTH=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health 2>/dev/null)
    if [ "$HEALTH" = "200" ]; then
        echo ""
        echo "============================================"
        echo "  更新成功！"
        echo "============================================"
        echo ""
        echo "  访问地址: http://114.132.55.119:8080"
        echo ""
        docker compose -f docker-compose-cloud.yml ps
        echo "============================================"
        exit 0
    fi
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
echo "[WARNING] 等待超时，请手动检查:"
echo "  docker compose -f docker-compose-cloud.yml logs --tail=30 app"
exit 0
