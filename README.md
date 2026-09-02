# 西游记：西行之路

[![CI](https://github.com/mishen9998/xiyouji/actions/workflows/ci.yml/badge.svg?branch=codex%2Fmultimodule-distributed)](https://github.com/mishen9998/xiyouji/actions/workflows/ci.yml?query=branch%3Acodex%2Fmultimodule-distributed)

基于 Spring Boot 3.4 与 Vue 3 的西游记 Roguelike 卡牌游戏，支持单人闯关和最多 5 人实时协作；后端采用五模块模块化单体，并完成 Redis 共享状态下的双实例一致性验证。

<p align="center">
  <img src="docs/assets/demo-home.webp" alt="西游记：西行之路游戏首页" width="100%">
</p>

> [!IMPORTANT]
> 本项目是模块化单体，不是微服务。`app-1` 与 `app-2` 是同一应用的两个运行实例，共享 MySQL 和 Redis，用于验证负载均衡、并发写入与跨实例实时通知。
>
> 项目采用 DDD 思想与模块化分层，但当前并非严格的纯领域架构：`domain` 仍使用 JPA/Jackson 注解，`application` 仍依赖 Spring、JWT 和 Jackson。相关解耦工作放在可运行性、测试和性能证据之后推进。

## 运行界面

以下图片均由当前 Docker Compose 双实例环境实测生成，而非概念图。

| 角色选择 | 双人房间 |
| --- | --- |
| ![孙悟空角色选择](docs/assets/demo-character-select.webp) | ![双人房间已选角并准备](docs/assets/demo-multiplayer-room.webp) |

| 多人地图 | 多人战斗 |
| --- | --- |
| ![多人 Roguelike 地图](docs/assets/demo-multiplayer-map.webp) | ![多人卡牌战斗](docs/assets/demo-multiplayer-battle.webp) |

当前尚未提供公网 Demo。启动本地环境后可访问：

- 游戏页面：<http://localhost:8080>
- Swagger UI：<http://localhost:8080/swagger-ui/index.html>
- 健康检查：<http://localhost:8080/actuator/health>

## 核心能力

### 游戏功能

- 单人 Roguelike 闯关：五种角色、随机地图、卡牌战斗、奖励选择、牌组调整和多层推进。
- 多人实时协作：房间创建/加入、唯一角色选择、准备状态、共享地图和多人战斗。
- 单个房间最多 5 人，服务端使用状态版本约束并发修改。
- 后端提供注册、登录和游客身份 API；当前前端默认自动获取游客 JWT。
- 前端通过 REST 获取权威状态，通过 STOMP WebSocket 接收实时变化通知。

### 工程能力

- Maven 五模块模块化单体，并使用 ArchUnit 持续检查关键依赖边界。
- Redis 保存房间、战斗和单人会话状态，为两个应用实例提供共享状态。
- Redisson 房间锁与会话锁串行化热点写入。
- 幂等键、请求指纹和状态版本共同处理网络重试、重复提交与并发覆盖。
- Redis Pub/Sub 负责跨实例事件通知；客户端断线后通过 REST 对账。
- Nginx 为两个 Spring Boot 实例提供 HTTP/WebSocket 负载均衡与基础限流。
- Actuator、Prometheus、Grafana 与 Zipkin 组成可观测性基线。
- 多阶段 Docker 构建和 Docker Compose 提供可复现的完整验收环境。

## 技术栈

| 领域 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.4.1、Spring Security、Spring Data JPA、Spring WebSocket |
| 数据 | MySQL 8、Redis 7、Redisson、Flyway |
| 前端 | Vue 3、TypeScript、Pinia、Vue Router、Vite、Three.js、STOMP |
| 接口 | REST、WebSocket/STOMP、OpenAPI/Swagger |
| 可观测性 | Actuator、Micrometer、Prometheus、Grafana、Zipkin |
| 工程化 | Maven、Docker、Docker Compose、Nginx、GitHub Actions |
| 测试 | JUnit 5、Mockito、ArchUnit、Testcontainers、JaCoCo、Compose 黑盒 E2E |

## 运行架构

```mermaid
flowchart LR
    Browser["Vue 3 客户端<br/>REST + STOMP WebSocket"]
    Nginx["Nginx<br/>静态资源 / 限流 / 负载均衡"]

    subgraph Runtime[同一模块化单体的两个运行实例]
        App1[Spring Boot app-1]
        App2[Spring Boot app-2]
    end

    MySQL[("MySQL<br/>用户与游戏目录数据")]
    Redis[("Redis<br/>房间 / 战斗 / 会话<br/>幂等记录 / 分布式锁 / Pub/Sub")]
    Prometheus[Prometheus]
    Grafana[Grafana]
    Zipkin[Zipkin]

    Browser -->|HTTP / WebSocket| Nginx
    Nginx --> App1
    Nginx --> App2

    App1 --> MySQL
    App2 --> MySQL
    App1 <--> Redis
    App2 <--> Redis

    Prometheus -->|抓取指标| App1
    Prometheus -->|抓取指标| App2
    Grafana --> Prometheus
    App1 -->|链路数据| Zipkin
    App2 -->|链路数据| Zipkin
```

Redis 中保存权威运行状态。Pub/Sub 只传递“状态发生变化”的通知，不承担可靠消息队列职责；客户端重连后重新通过 REST 获取完整状态。

## 模块设计

```text
xiyouji-parent
├── xiyouji-common
├── xiyouji-domain
├── xiyouji-application
├── xiyouji-infrastructure
└── xiyouji-bootstrap
```

| 模块 | 主要职责 |
| --- | --- |
| `xiyouji-common` | 公共异常、错误码和共享类型 |
| `xiyouji-domain` | 游戏实体、值对象与核心规则 |
| `xiyouji-application` | 用例服务，以及 Repository、锁、状态存储和事件 Port |
| `xiyouji-infrastructure` | JPA、Redis、Redisson、Pub/Sub 等适配器实现 |
| `xiyouji-bootstrap` | Spring Boot 启动、Controller、安全配置和静态资源装配 |

总体依赖方向为：

```text
common ← domain ← application ← infrastructure ← bootstrap
```

当前使用 4 条 ArchUnit 规则检查：

- Controller 不直接依赖 Repository。
- Service 不反向依赖 Controller。
- 应用服务通过 Port 访问持久化能力。
- 游戏模型不依赖 Redis 或 Redisson。

这套结构用于控制模块边界，不表示当前已经实现完全框架无关的领域层。

## 关键技术难点与取舍

| 问题 | 当前方案 | 验证方式 | 取舍 |
| --- | --- | --- | --- |
| 请求可能落到不同实例 | Redis 保存房间、战斗和会话权威状态 | `app-1` 创建房间、`app-2` 加入并读取 | 增加 Redis 运行依赖，换取实例无关的状态访问 |
| 多个玩家并发修改同一房间 | Redisson 房间粒度锁、预期状态版本和版本递增 | 并发加入与状态冲突测试 | 热点房间写入被串行化，优先保证一致性 |
| 网络重试造成重复写入 | `X-Idempotency-Key`、请求指纹、执行状态和响应重放 | 幂等与命令守卫测试 | Redis 中保留短期幂等记录，需要额外存储空间 |
| Pub/Sub 不保证可靠送达 | 事件携带 `eventId/stateVersion`；客户端通过 REST 对账 | 跨实例 STOMP 黑盒 E2E | 接受短暂通知丢失，不把 Pub/Sub 当消息队列 |
| 模块边界随开发退化 | Maven 模块、Port/Adapter 和 ArchUnit 规则 | 4 条架构测试进入 Maven 验证 | 暂时保留部分框架耦合，后续逐步净化领域层 |
| 双实例故障难观察 | Actuator、Prometheus、Grafana、Zipkin 和告警规则 | 健康检查、`promtool` 与故障演练 | 当前是本地部署基线，不等同于生产监控体系 |

## 快速启动

### Docker 双实例环境

需要 Docker Desktop 或兼容的 Docker Engine。

Windows PowerShell：

```powershell
Copy-Item .env.example .env
```

Linux/macOS：

```bash
cp .env.example .env
```

编辑 `.env`，至少替换数据库密码、JWT 密钥和 Grafana 密码，然后启动：

```bash
docker compose up -d --build --wait
docker compose ps
```

首次构建和两个 JVM 冷启动可能需要数分钟。停止服务：

```bash
docker compose down
```

该命令默认保留数据卷。只有明确需要删除本地测试数据时才使用 `docker compose down -v`。

更新镜像并单独重建应用容器后，应同时重建 Nginx 以刷新上游解析：

```bash
docker compose up -d --force-recreate app-1 app-2 nginx
```

### Standalone 学习模式

Standalone 模式使用 H2、内存状态存储和 JVM 本地锁，不需要 MySQL 或 Redis，适合查看业务规则和调试接口。

需要 JDK 17 与 Node.js 20：

```powershell
npm ci --prefix frontend-vue
npm run build --prefix frontend-vue
.\mvnw.cmd -B -pl xiyouji-bootstrap -am package -DskipTests
java -jar xiyouji-bootstrap/target/xiyouji-bootstrap-1.0.0.jar --spring.profiles.active=standalone
```

Linux/macOS 将 `.\mvnw.cmd` 替换为 `./mvnw`。

## 状态一致性约定

- 分布式环境不在 Redis 故障时回退到本机内存，避免两个实例产生不同状态。
- 房间写操作使用 `xiyouji:lock:room:{roomCode}`。
- 单人会话写操作使用 `xiyouji:lock:session:{sessionId}`。
- 写命令使用 `X-Idempotency-Key` 标识一次业务意图。
- 修改已有状态时使用 `X-Expected-State-Version` 防止旧数据覆盖新数据。
- 幂等记录保存请求指纹和首次成功响应；同一个键更换请求体会被拒绝。
- WebSocket 事件携带状态版本；客户端丢弃旧事件，并在重连后读取权威状态。
- `DataInitializer` 使用一次性分布式锁，避免两个实例重复执行种子初始化。

## 验证证据

最近一次完整本地验收记录：

| 验证项 | 结果 |
| --- | --- |
| 前端 TypeScript 检查与生产构建 | 通过 |
| Maven 测试 | 76 个全部通过，0 失败、0 错误、0 跳过 |
| Testcontainers 集成测试 | 上述测试中包含 3 个 MySQL/Redis 容器集成测试 |
| Compose 黑盒 E2E | 2 个全部通过 |
| 跨实例通信 | 已验证共享房间状态与跨实例 STOMP 通知 |
| 并发房间容量 | 已验证 20 个并发加入请求不会突破 5 人上限 |
| Prometheus 规则 | 3 条规则通过 `promtool` 校验 |
| Kubernetes 清单 | 6 个资源通过 kubeconform 离线 schema 校验 |

> [!NOTE]
> 以上是可复现的工程验收记录，不是生产环境 SLA 或性能结论。README 不展示当前覆盖率百分比；下一阶段将建立聚合覆盖率非零门禁，并优先补足关键路径测试。

### 基础验证

```powershell
npm ci --prefix frontend-vue
npm run typecheck --prefix frontend-vue
npm run build --prefix frontend-vue
.\mvnw.cmd -B -pl xiyouji-bootstrap -am clean verify
docker compose config
```

Docker Engine 可用时，Maven 验证会运行 MySQL/Redis Testcontainers 集成测试。JaCoCo 五模块聚合报告生成在：

```text
xiyouji-bootstrap/target/site/jacoco-aggregate/
```

### 双实例 Compose E2E

```powershell
docker compose -p xiyouji-e2e -f docker-compose.yml -f docker-compose.e2e.yml up -d --build --wait

.\mvnw.cmd -B -pl xiyouji-bootstrap -am -Pcompose-e2e `
  -Dtest=DistributedComposeE2ETest `
  "-Dsurefire.failIfNoSpecifiedTests=false" `
  "-DfailIfNoTests=false" test

docker compose -p xiyouji-e2e -f docker-compose.yml -f docker-compose.e2e.yml down -v
```

覆盖文件临时发布 `18081` 和 `18082`，使测试可以分别直连两个应用实例；正常访问仍只通过 Nginx 的 `8080` 端口。

### 故障演练

双实例环境启动后，可运行：

```powershell
.\scripts\distributed-failure-drill.ps1
```

脚本依次验证：停止 `app-1` 后由 `app-2` 继续提供服务、Redis 故障时应用退出就绪状态、Redis 恢复后应用恢复，以及 Prometheus 能否抓取两个实例。

## CI

[GitHub Actions 工作流](.github/workflows/ci.yml)执行：

1. 前端依赖安装、TypeScript 检查与生产构建。
2. Maven 测试与 JaCoCo 聚合报告生成。
3. Docker Compose 配置校验。
4. Kubernetes 清单离线 schema 校验。
5. 多阶段 Docker 镜像构建。
6. 双实例 Compose 启动与健康检查。
7. Prometheus 告警规则校验。
8. 跨实例 REST/WebSocket 黑盒 E2E。
9. 配置密钥时执行可选 SonarQube 分析。

## k6 当前状态

[distributed-smoke.js](performance/k6/distributed-smoke.js)目前只是 Nginx 健康接口与实例路由的 smoke 模板：

```bash
k6 run \
  -e BASE_URL=http://localhost:8080 \
  -e VUS=20 \
  -e DURATION=1m \
  performance/k6/distributed-smoke.js
```

脚本中的阈值只用于 smoke 失败判定，不构成真实业务性能结论。目前尚未完成包含登录、创建房间、加入房间、状态修改和战斗操作的业务压测，因此 README 和简历中不声明业务 P95、吞吐量或容量上限。

后续会在固定机器配置、数据规模和并发模型后，输出可复现的 HTML/JSON 压测报告。

## Kubernetes 当前状态

[k8s/](k8s/) 中保存的是部署草案，当前只完成 kubeconform 离线 schema 校验，尚未在真实 Kubernetes 集群中部署验收。

```bash
docker run --rm \
  -v "${PWD}/k8s:/work:ro" \
  ghcr.io/yannh/kubeconform:v0.6.7 \
  -strict -summary /work
```

草案默认部署两个应用副本，并假设 MySQL 和 Redis 由集群外部的托管服务或已有 Service 提供。正式部署前仍需替换镜像地址与 Secret，并验证 Ingress WebSocket、健康检查、滚动升级和扩缩容。详见 [k8s/README.md](k8s/README.md)。

## 已知边界与演进路线

- [x] 整理并发布模块化单体与双实例运行基线。
- [x] 建立 Compose、CI、跨实例黑盒 E2E 与 K8s 离线清单校验。
- [x] 增加真实页面截图、Mermaid 架构图和技术取舍说明。
- [ ] 为 JaCoCo 聚合覆盖率建立非零门禁，并增加前端单元测试与 Playwright E2E。
- [ ] 设计真实业务 k6 场景，固定环境并输出可复现报告。
- [ ] 拆分体积较大的 Service 和 Controller。
- [ ] 逐步移除领域层与应用层的框架依赖。
- [ ] 在真实 Kubernetes 集群验证部署、扩缩容、滚动升级与故障恢复。

当前没有公网 Demo；前端尚未接入正式自动化测试；部分 Service/Controller 仍较大。公开分发前还应补充项目 LICENSE，以及插画、字体等素材的来源与授权说明。

对于求职展示，本项目优先提供可运行代码、通过的 CI、复现命令和清晰的技术取舍，而不是继续叠加尚未形成实际价值的中间件。
