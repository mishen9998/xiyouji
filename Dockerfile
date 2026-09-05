# syntax=docker/dockerfile:1.7

# Stage 1: compile the Vue application in a reproducible Node image.
FROM node:20.18.1-alpine3.20 AS frontend-build
WORKDIR /workspace/frontend-vue

COPY frontend-vue/package.json frontend-vue/package-lock.json ./
RUN npm ci
COPY frontend-vue/ ./
RUN npm run build

# Stage 2: compile all Maven modules and assemble the Spring Boot jar.
FROM maven:3.9.9-eclipse-temurin-17 AS backend-build
WORKDIR /workspace

COPY pom.xml ./
COPY xiyouji-common/pom.xml xiyouji-common/pom.xml
COPY xiyouji-domain/pom.xml xiyouji-domain/pom.xml
COPY xiyouji-application/pom.xml xiyouji-application/pom.xml
COPY xiyouji-infrastructure/pom.xml xiyouji-infrastructure/pom.xml
COPY xiyouji-bootstrap/pom.xml xiyouji-bootstrap/pom.xml
COPY xiyouji-common/src xiyouji-common/src
COPY xiyouji-domain/src xiyouji-domain/src
COPY xiyouji-application/src xiyouji-application/src
COPY xiyouji-infrastructure/src xiyouji-infrastructure/src
COPY xiyouji-bootstrap/src xiyouji-bootstrap/src
COPY assets/images assets/images
COPY --from=frontend-build /workspace/frontend-vue/dist/ frontend-vue/dist/

# Tests run in CI before the image is built. Skip test compilation here as
# well, keeping the production image build fast and independent of test-only
# dependencies.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -Dmaven.test.skip=true package

# Stage 3: small runtime image. Only the executable bootstrap jar is shipped.
FROM eclipse-temurin:17.0.13_11-jre-alpine
LABEL org.opencontainers.image.title="xiyouji-roguelike"
LABEL org.opencontainers.image.description="西游记 Roguelike 多模块 Spring Boot 应用"
WORKDIR /app

RUN apk add --no-cache tzdata curl \
    && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo Asia/Shanghai > /etc/timezone

COPY --from=backend-build /workspace/xiyouji-bootstrap/target/xiyouji-bootstrap-1.0.0.jar app.jar

EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=180s --retries=5 \
  CMD curl -fsS http://127.0.0.1:${SERVER_PORT:-8080}/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=200", \
  "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
