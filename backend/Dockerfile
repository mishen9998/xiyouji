# ========================================
# 轻量运行镜像 (JAR 在宿主机已构建好)
# ========================================
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="xiyouji-roguelike"
LABEL description="西游记 Roguelike 卡牌游戏 - 分布式实例"

WORKDIR /app

# 直接复制宿主机构建好的 JAR
COPY target/xiyouji-roguelike-1.0.0.jar app.jar

# 时区设置 + 健康检查工具
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

EXPOSE 8080 8081 8082

# JVM 容器化优化参数
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/app.jar"]
