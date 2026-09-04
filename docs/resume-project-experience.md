# 简历项目经历（可直接粘贴）

## 西游记：西行之路｜多人实时协作 Roguelike 游戏平台

**个人项目｜全栈开发**

基于 Spring Boot 3 与 Vue 3 开发的 Web Roguelike 卡牌游戏，支持单人闯关和最多 5 人实时协作；后端采用五模块模块化单体，通过 Redis 共享状态验证同一应用的双实例运行，并围绕并发一致性、请求幂等、跨实例通知、自动化测试和可复现部署建立工程闭环。

**技术栈：** Java 17、Spring Boot 3.4、Spring Security、Spring Data JPA、WebSocket/STOMP、MySQL 8、Redis 7、Redisson、Vue 3、TypeScript、Pinia、Three.js、Nginx、Docker Compose、GitHub Actions、JUnit 5、Testcontainers、ArchUnit、Vitest、Playwright、k6

### 项目成果

- 基于 DDD 思想与模块化分层，将后端拆分为 `common`、`domain`、`application`、`infrastructure`、`bootstrap` 五个 Maven 模块，并通过 4 条 ArchUnit 规则持续校验 Controller、Service、Repository 与基础设施的关键依赖边界。
- 针对双实例下的多人状态一致性问题，以 Redis 保存房间、战斗和会话权威状态，结合 Redisson 房间粒度锁、状态版本校验以及“幂等键 + 请求指纹 + 响应重放”处理并发写入与网络重试；使用 Redis Pub/Sub 通知状态变化，客户端断线后通过 REST 重新对账。
- 使用 Vue 3、TypeScript、Pinia、Three.js 与 STOMP 完成单人闯关、角色选择、多人房间、共享地图与协作战斗；完成 `app-1` 建房、`app-2` 跨实例读取/加入及跨实例 WebSocket 通知验证。
- 建立 GitHub Actions 持续集成，自动执行前端类型检查与构建、76 个后端测试、Vitest、Playwright、Testcontainers、Compose 黑盒验证及 JaCoCo 五模块聚合非零回归门禁。
- 使用多阶段 Docker 构建与 Compose 一键启动，并编写 12 步跨实例 k6 业务链路；在固定本机 Docker 环境中以 5 transaction/s 连续执行 3 轮、每轮 5 分钟，共完成 4,502 笔完整事务，业务成功率 100%，跨实例状态不一致、HTTP 5xx 与丢弃迭代均为 0；在 12 transaction/s 探索档识别出房间创建全局锁瓶颈，并形成原子唯一性与细化锁粒度的改进方案。

### 30 秒面试介绍

> 这是一个支持单人和最多 5 人协作的 Roguelike 卡牌游戏。我没有把它包装成微服务，而是采用五模块模块化单体，因为当前规模下这样既能保持清晰边界，也能避免不必要的分布式治理成本。为了验证横向运行时问题，我将同一应用部署为两个实例，通过 Redis 共享权威状态。项目中的主要难点是多人同时修改同一房间以及请求可能落到不同实例，因此我组合使用了 Redisson 房间锁、状态版本和幂等请求机制，同时将 Pub/Sub 只用于通知，客户端最终通过 REST 对账。项目已经具备自动化 CI、浏览器 E2E、Compose 一键部署和可复现的 k6 业务测试报告。

## 表述边界

- 说“模块化单体”，不说“微服务架构”。
- 说“DDD 思想/模块化分层”，不说“严格 DDD”。
- 压测数据是本机可复现基线，不是生产 SLA，不声明未建立的 P95 门槛或容量上限。
- Kubernetes 仅是通过离线 schema 校验的部署草案，不说“生产级 Kubernetes”。
- 这是复杂有状态应用的全栈工程项目，不直接包装成 Agent 项目。投递 AI 全栈/Agent 岗位时，建议另外补一个真实使用模型调用、Tool Calling、RAG 或评测体系的小型项目。
