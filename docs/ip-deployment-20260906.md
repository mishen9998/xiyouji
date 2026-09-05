# 公网 IP 演示部署（2026-09-06）

入口：http://114.132.55.119/ 。使用 HTTP/WS，不需要续费域名；未配置 HTTPS/WSS。演示建议使用游客模式。

运行版本为 `release-20260906-rc1`，业务提交 `24c9497`。GitHub main 还包含团队开发和本部署说明。当前服务器为 Ubuntu x86_64、2 GiB 内存，实际 Web 入口是 1Panel/OpenResty。

## 运行位置

- 发布目录：`/opt/xiyouji-releases/release-20260906-rc1`
- Compose 项目：`xiyouji-live`
- 私有配置：发布目录中的 `compose.private.json`，权限 600，包含随机密钥，禁止上传 GitHub。
- 应用仅监听宿主机 `127.0.0.1:18088`，OpenResty 的独立 IP 站点代理到此端口。
- 代理配置：`/opt/1panel/www/conf.d/xiyouji-ip.conf`，示例见 `nginx-ip-demo.conf.example`。
- 应用/MySQL/Redis 内存上限分别为 512/320/128 MiB；JVM 最大堆 256 MiB，MySQL 缓冲池 64 MiB。
- MySQL、Redis 不发布宿主机端口；其他项目的数据库端口与容器未修改。

首页加载动画依赖 `/actuator/health`，只放行这个精确路径，匿名响应仅含 UP 状态；Swagger、其他 Actuator 和实例信息被代理屏蔽。

## 旧版本清理

经用户明确授权，核验旧库用户数为 0、旧 Redis 键数为 0 后，删除旧容器 `xiyouji-app/mysql/redis`、旧专属卷 `xiyouji_mysql_data` 和 `xiyouji_redis_data`、旧项目网络、旧应用镜像及 `/www/wwwroot/xiyouji`。新版使用独立数据卷，旧版已不保留服务器回退副本。

`labsys` 和 1Panel 其他服务保留。未执行全局 Docker prune，也未删除供新版使用的 MySQL/Redis 基础镜像。

## 验证与边界

已从开发机公网访问验证首页 200、健康状态 UP、Swagger 404，并通过实际 WS/STOMP 握手及房间状态通知测试。公网测试只有一个实例，不将复用的跨实例测试类名称当成双实例部署证据。

Playwright Chromium 公网回归 9/9 通过（单 worker，约 2.6 分钟），覆盖桌面/手机奖励界面、三人延迟加入、REST 后备同步、中文身份刷新和单人创建会话等。部分界面场景使用 API mock，不表示完整多人通关均已验收。

现有内存适用于小规模演示，不声明并发容量或高可用。异机备份、正式监控、TLS 尚未配置。仓库中的备份工具默认读取 compact YAML；本次服务器使用私有 JSON 及不同 MySQL 参数，不能直接套用默认备份命令，需先对齐配置再演练。

## 运维命令

```bash
cd /opt/xiyouji-releases/release-20260906-rc1
docker compose -p xiyouji-live -f compose.private.json ps
docker compose -p xiyouji-live -f compose.private.json logs --tail 100 app
```

新电脑和协作流程见 [团队开发说明](team-development.md)。服务器不作为日常开发工作区，修改应提交 GitHub 后以固定版本发布。
