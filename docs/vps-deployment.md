# VPS 精简部署指南

> 当前状态：这是可执行的单实例展示部署方案，还需要一台 Linux VPS、域名和 HTTPS/WSS 现场验收后，才能在简历中写“在线 Demo”。它不是高可用或零停机方案。

## 1. 生成固定提交镜像

1. 先确认当前提交的 GitHub Actions `CI` 全部通过。
2. 在 Actions 页面手动运行 `Release container image`，或推送 `v*` 版本标签。
3. 记录输出镜像的 `sha-<short-sha>` 标签，例如 `ghcr.io/mishen9998/xiyouji:sha-1a2b3c4`。
4. 确认 GHCR Package 可被 VPS 拉取：公开展示可将 Package 设为 Public，私有部署则先在 VPS 执行 `docker login ghcr.io`。

发布工作流会检查同一提交是否存在成功的 CI，再构建和发布镜像。CI 支持手动触发；如果版本标签所在提交尚无绿色 CI，应先运行 CI。正式部署建议记录并使用 GHCR 的 `@sha256:...` 镜像 digest，部署脚本同时支持固定 `sha-*` 标签和 digest。

## 2. 准备 VPS

推荐 Ubuntu x86_64、2 核 4 GB 及以上。VPS 只需要 Docker Engine、Docker Compose v2、Git 和 `curl`，不需要 Java、Maven 或 Node.js。

```bash
git clone https://github.com/mishen9998/xiyouji.git
cd xiyouji
cp .env.cloud.example .env.cloud
chmod 600 .env.cloud
```

编辑 `.env.cloud`：

- `APP_IMAGE` 使用上一步的固定 `sha-*` 标签，不使用 `latest`。
- `DB_PASSWORD` 使用至少 20 个字符的随机密码。
- `JWT_SECRET` 使用至少 64 个字符的随机值。
- `CORS_ORIGINS` 和 `PUBLIC_BASE_URL` 都改为真实 HTTPS 域名。
- 保持 `HTTP_BIND=127.0.0.1`，不要将 Spring Boot 的 8080 端口直接暴露到公网。

## 3. 部署

```bash
bash scripts/deploy-cloud.sh
```

脚本会校验密钥与镜像标签、拉取镜像、启动 MySQL/Redis/App，并验证健康接口与前端首页。任一步失败都会返回非零状态并打印应用日志。再次执行同一命令即可更新到 `.env.cloud` 指定的镜像。

## 4. 配置 HTTPS/WSS

可使用 Caddy、Nginx 或宝塔反向代理到 `127.0.0.1:8080`。公网安全组只开放 22、80 和 443；禁止对公网放行 8080。

Caddy 最小示例（替换域名）：

```caddyfile
demo.example.com {
    @internal path /actuator* /swagger-ui* /v3/api-docs*
    respond @internal 404
    reverse_proxy 127.0.0.1:8080
}
```

Caddy 会自动处理 WebSocket Upgrade。使用 Nginx/宝塔时，需显式转发 `Upgrade` 和 `Connection` 头。

## 5. 部署后验收

```bash
curl --fail https://demo.example.com/
```

在开发机执行浏览器回归：

```powershell
$env:PLAYWRIGHT_BASE_URL = 'https://demo.example.com'
npm run test:e2e --prefix frontend-vue
```

然后用普通窗口和无痕窗口完成一次多人房间验收，确认 HTTPS、WSS、跨窗口状态更新和游戏开局正常。

## 安全与能力边界

宝塔已有 HTTPS 站点可参考 [反向代理片段](baota-proxy.conf.example)，合并到该站点的 server 配置，保留原证书设置；先检查配置并 reload，再验证 HTTPS 页面和 WSS 握手。不要覆盖其他站点或把片段直接当作完整 nginx.conf。

升级前执行 [备份和隔离恢复](backup-restore.md)，并记录实际恢复结果。

- 单 App 更新会有短暂停机，不声明高可用或零停机。
- 对外禁用 Actuator 详情、Swagger 和 API Docs；健康检查由容器内部执行。
- 上线前确认插画、字体等素材的授权，并补充适用的 LICENSE。
- Kubernetes 目录仍是通过 schema 校验的部署草案，不属于这条 VPS 验收链路。
