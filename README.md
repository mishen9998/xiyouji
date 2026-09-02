# 西游记：西行之路

西游记题材 Roguelike 卡牌构筑游戏。当前版本是“**Maven 多模块模块化单体 + Redis 共享状态 + Nginx 双实例运行**”：第一阶段不拆微服务，保留现有 REST API 和 Vue 页面，同时具备可以在简历中解释的模块边界、分布式锁、跨实例 WebSocket 广播和容器化部署。

## 架构

```text
浏览器 ── HTTP/WebSocket ──> Nginx
                              │
                    ┌─────────┴─────────┐
                    │                   │
                 app-1               app-2
                    │                   │
                    └───────┬───────────┘
                            │
                    MySQL + Redis
                            │
              Redis Pub/Sub（实时事件通知）
```

两个应用实例使用同一个镜像、同一个 MySQL 和 Redis。Redis 中的房间/战斗状态是权威状态，Pub/Sub 只负责把状态变化通知到各实例的本地 WebSocket 客户端；客户端重连后通过 REST 重新读取完整状态。

## Maven 模块

```text
xiyouji-parent
├── xiyouji-common          # 错误码、公共异常、共享类型
├── xiyouji-domain          # 游戏模型与规则（不访问 Redis）
├── xiyouji-application      # 用例服务、Repository Port、锁/事件端口
├── xiyouji-infrastructure   # JPA Repository、Redis/Redisson/锁/事件适配器
└── xiyouji-bootstrap        # Spring Boot 启动类、Controller、配置、静态资源
```

依赖方向固定为 `common ← domain ← application ← infrastructure ← bootstrap`。application 只声明面向用例的 Port，JPA Repository 实现在 infrastructure；业务代码按 `auth`、`catalog`、`singleplayer`、`multiplayer`、`platform` 组织，Controller 不直接访问 Repository。

## 本地运行

### 独立模式（不需要 MySQL/Redis）

需要 JDK 17 和 Node.js 18+：

```bash
npm ci --prefix frontend-vue
npm run build --prefix frontend-vue
bash mvnw -B -pl xiyouji-bootstrap -am package -DskipTests
java -jar xiyouji-bootstrap/target/xiyouji-bootstrap-1.0.0.jar --spring.profiles.active=standalone
```

浏览器访问 <http://localhost:8080>。此模式使用 H2、内存会话和 JVM 锁，适合学习领域规则与接口。

### Docker 双实例模式

```bash
copy .env.example .env       # Windows；Linux/macOS 使用 cp
docker compose up -d --build
docker compose ps
```

只对外暴露 Nginx：<http://localhost:8080>。MySQL、Redis 和两个应用实例只在 Compose 网络内可访问。监控组件默认也不发布宿主机端口，可按需增加本地端口映射。

更新应用镜像后如果执行了单独的应用容器重建，需同时刷新 Nginx 的上游解析：

```bash
docker compose up -d --force-recreate app-1 app-2 nginx
```

这样可避免旧容器 IP 被 Nginx 缓存导致的短暂 502。

### 双实例端到端验收

默认 Compose 不发布应用端口，避免绕过 Nginx。需要验证两个实例之间的真实 REST/WebSocket 通信时，使用只用于测试的覆盖文件：

```bash
docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build --wait
# Windows:
.\\mvnw.cmd -B -pl xiyouji-bootstrap -am -Pcompose-e2e -Dtest=DistributedComposeE2ETest '-Dsurefire.failIfNoSpecifiedTests=false' '-DfailIfNoTests=false' test
# Linux/macOS:
bash mvnw -B -pl xiyouji-bootstrap -am -Pcompose-e2e -Dtest=DistributedComposeE2ETest -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false test
docker compose -f docker-compose.yml -f docker-compose.e2e.yml down
```

测试临时使用 `localhost:18081` 和 `localhost:18082` 直连两个实例，覆盖房间共享状态、跨实例 STOMP 通知和 20 个并发加入请求；生产/日常访问仍只使用 Nginx 的 `localhost:8080`。

### 故障演练与告警

在默认双实例 Compose 已启动且 Docker Engine 可用时，可以运行 Windows PowerShell 故障演练：

```powershell
.\scripts\distributed-failure-drill.ps1
```

脚本会依次停止/恢复 `app-1`，验证 Nginx 仍由 `app-2` 提供服务；再停止/恢复 Redis，验证两个应用进入非就绪状态并恢复；同时检查 Prometheus 是否抓到两个实例。应用冷启动可能需要约两分钟，因此脚本默认等待 180 秒，并在异常时尽量恢复被停止的容器。

Prometheus 已加载 [alert_rules.yml](monitoring/prometheus/alert_rules.yml)，包含单实例不可用、集群全部不可用和 HTTP 5xx 比例过高告警。规则文件通过 Compose 挂载，并可用以下命令校验：

```bash
docker compose exec -T prometheus promtool check rules /etc/prometheus/alert_rules.yml
```

### 分布式运行与安全约束

- `distributed`/`prod` 配置不再为数据库密码和 JWT 密钥提供默认值，必须通过环境变量或 Secret 注入；`dev`/`standalone` 仍保留本地学习用默认值。Compose 对 DB/JWT/Grafana 密钥启用 fail-fast，缺少变量会拒绝启动。
- 生产模式只匿名开放 `/actuator/health` 和 `/actuator/prometheus`；`/actuator/info`、`/actuator/metrics` 等管理端点需要 JWT。
- HikariCP、Lettuce 和 Zipkin 采样率均可通过环境变量调优，默认链路采样率为 10%，避免双实例压测时产生过量追踪数据。
- Nginx 游戏 API 默认按 IP 限制为 10 r/s；只读的 `/api/instance/**` 路由不占用该配额，用于监控和压测路由探针，触发限流时返回标准 `429`。
- [distributed-smoke.js](performance/k6/distributed-smoke.js) 是健康接口与实例路由的 k6 smoke 阈值模板；它不是真实业务压测，也不作为业务 P95 成果：

  ```bash
  k6 run -e BASE_URL=http://localhost:8080 -e VUS=20 -e DURATION=1m performance/k6/distributed-smoke.js
  ```

停止：

```bash
docker compose down
```

## 配置策略

- `standalone`：H2 + 内存存储，不创建 Redis/Redisson 客户端。
- `distributed`：Flyway 管理表结构，Hibernate 使用 `ddl-auto: validate`；Redis/Redisson 不可用时状态写入和锁操作返回 503，不回退到本机内存。
- 房间所有写操作使用 `xiyouji:lock:room:{roomCode}`；房间和战斗状态保存时递增 `stateVersion`。
- 所有业务写命令（登录除外）必须携带 `X-Idempotency-Key`；已有资源的写命令还必须携带 `X-Expected-State-Version`。版本冲突返回 409，客户端刷新完整状态后再由用户重新操作。
- 幂等记录在 Redis 保留 10 分钟，保存请求指纹、执行状态和完整成功响应；相同键重复请求直接重放第一次响应，换请求体复用键返回 409。会话存储还会拒绝低版本对象覆盖新版本。
- 单人会话绑定 JWT owner，并使用 `xiyouji:lock:session:{sessionId}` 串行化写入；WebSocket 事件带 `eventId/stateVersion`，客户端丢弃旧版本并在重连后 REST 对账。
- `DataInitializer` 使用一次性分布式锁，两个实例同时启动也只执行一份种子初始化。
- 敏感值从 `.env` 注入；生产环境必须替换示例密码和 JWT 密钥。

## API 与 WebSocket

现有 REST 路径保持兼容。实时端点为 `/ws`，房间频道为 `/topic/room/{roomCode}`，战斗频道为 `/topic/room/{roomCode}/battle`。Nginx 为 `/ws` 配置 HTTP/1.1 Upgrade、长连接超时和 Origin 白名单。

## 验证命令

```bash
npm --prefix frontend-vue ci
npm --prefix frontend-vue run typecheck
npm --prefix frontend-vue run build
bash mvnw -B -pl xiyouji-bootstrap -am verify
docker compose config
```

`clean verify` 会在 `xiyouji-bootstrap/target/site/jacoco-aggregate/` 生成五模块聚合覆盖率报告。当前验证记录为 76 个测试全部通过（0 失败、0 跳过，含 3 个 MySQL/Redis Testcontainers 集成测试）；Compose E2E 另有 2 个跨实例 REST/WebSocket 黑盒测试。故障演练脚本用于验证容器级恢复路径。

Kubernetes 可校验草案位于 `k8s/`，当前只完成离线 schema 校验，尚未在真实集群中部署验收。有可用 Kubernetes API Server 时可使用：

```bash
kubectl apply --dry-run=client -f k8s/
```

当前 MVP 不要求 Kubernetes API Server；本机无集群时使用 kubeconform 做离线 schema 校验（CI 也执行该校验）：

```bash
docker run --rm -v "${PWD}/k8s:/work:ro" ghcr.io/yannh/kubeconform:v0.6.7 -strict -summary /work
```

部分 kubectl 版本即使指定 `--dry-run=client` 仍会请求 API discovery；因此没有 Docker Desktop Kubernetes 时出现 `localhost:80` 连接错误属于环境限制，不代表清单无效。Docker Desktop 启动后，`mvn verify` 会自动运行 MySQL/Redis Testcontainers 集成测试；项目已在 `xiyouji-bootstrap/src/test/resources/docker-java.properties` 固定 Docker Engine 29 所需的 API 版本。CI 同时执行 Maven 验证、Compose 配置校验、镜像构建和双实例端到端测试。

## 简历可描述的技术亮点

- 设计并落地 Maven 五模块模块化单体，明确领域、应用、基础设施和启动层依赖方向。
- 使用 Redisson 房间粒度分布式锁，解决双实例下并发加入、出牌、领奖的竞态问题。
- 使用 Redis Pub/Sub + 事件信封实现跨实例 WebSocket 广播，并用状态版本支持重连对账。
- 使用多阶段 Docker 构建、Nginx 负载均衡、Actuator/Prometheus/Grafana/Zipkin 形成可观测部署基线。

## 开发约定

- 新业务先进入 `domain` 规则或 `application` 用例，再由 `infrastructure` 接入外部系统。
- 任何房间状态写操作必须持有统一房间锁，并在保存后发布事件。
- Redis Pub/Sub 不作为可靠消息队列；客户端始终应保留 REST 状态恢复路径。
- 提交前运行 `bash mvnw -B -pl xiyouji-bootstrap -am verify` 和前端 typecheck/build。
