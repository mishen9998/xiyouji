# BOSS 直聘项目经历（可直接粘贴）

## 西游记：西行之路｜多人实时协作 Roguelike 卡牌游戏平台

**个人项目｜AI 协作全栈开发**

### 项目描述

- 基于 Java 17、Spring Boot 3 和 Vue 3 从 0 到 1 独立开发的西游题材 Roguelike 卡牌游戏，支持单人闯关、角色养成、随机地图，以及最多 5 名玩家组队协作战斗。
- 独立负责需求梳理、产品设计、系统架构、前后端开发、数据库设计、自动化测试、Docker 部署和线上演示，完成从创意到可运行 Web 产品的完整交付。前端使用 Vue 3、TypeScript、Pinia，后端使用 Spring Security、Spring Data JPA，MySQL 保存用户及基础数据，Redis 保存游戏与房间实时状态。
- 围绕多人游戏中的核心问题，使用 WebSocket/STOMP 实现房间及战斗状态实时同步，并通过 Redisson 分布式锁、状态版本和幂等机制，避免多人同时操作互相覆盖以及网络重试造成重复提交。
- 熟练运用 AI 辅助完成需求拆解、代码实现、问题定位、测试设计和技术文档沉淀，并对 AI 生成结果进行代码审查、自动化验证和工程化落地；同时通过模块化设计、GitHub Actions 持续集成、Docker 一键部署及监控压测保证项目质量。

**技术栈：** Java 17、Spring Boot 3.4、Spring Security、Spring Data JPA、MySQL 8、Redis 7、Redisson、WebSocket/STOMP、Vue 3、TypeScript、Pinia、Three.js、Nginx、Docker Compose、GitHub Actions、JUnit 5、Testcontainers、ArchUnit、Vitest、Playwright、k6

### 项目业绩

- 从 0 到 1 独立完成需求分析、UI 交互、Java 后端、Vue 3 前端、MySQL/Redis 数据层及 Docker 部署，交付可在线访问、可独立运行的完整产品，体现 AI 协作下的全栈开发与工程落地能力。
- 实现用户注册登录、游客存档、随机地图、卡牌战斗、多人房间和共享战斗等核心功能；基于 WebSocket/STOMP 完成最多 5 人实时协作，使玩家的选角、准备、地图和战斗进度能够及时同步。
- 面向双实例部署场景，以 Redis 统一保存房间、战斗和会话状态，结合 Redisson 房间级分布式锁、状态版本校验及幂等请求机制，解决并发修改、重复提交和跨实例状态一致性问题。
- 基于 DDD 思想与模块化分层，将后端划分为 5 个 Maven 模块，并使用 4 条 ArchUnit 规则自动检查 Controller、Service、Repository 与基础设施之间的关键依赖边界，提升代码可维护性。
- 建立覆盖开发到交付的质量保障流程：GitHub Actions 自动执行构建、类型检查及测试；79 个 Maven 测试全部通过，并结合 JUnit 5、Testcontainers、Vitest、Playwright 和 Compose 黑盒测试验证数据库、缓存、前端页面及跨实例通信。
- 通过 Nginx、Docker Compose 完成双实例部署和一键启动；在固定本机 Docker 测试环境中执行 k6 完整业务压测，累计完成 4,502 笔事务，业务成功率 100%，状态不一致、HTTP 5xx 和丢弃迭代均为 0，并根据高压力测试结果定位出全局锁性能瓶颈及后续优化方向。

### 30 秒面试介绍

> 这是一个支持单人和最多 5 人协作的 Roguelike 卡牌游戏。我没有把它包装成微服务，而是采用五模块模块化单体，因为当前规模下这样既能保持清晰边界，也能避免不必要的分布式治理成本。为了验证横向运行时问题，我将同一应用部署为两个实例，通过 Redis 共享权威状态。项目中的主要难点是多人同时修改同一房间以及请求可能落到不同实例，因此我组合使用了 Redisson 房间锁、状态版本和幂等请求机制，同时将 Pub/Sub 只用于通知，客户端最终通过 REST 对账。项目已经具备自动化 CI、浏览器 E2E、Compose 一键部署和可复现的 k6 业务测试报告。

## 表述边界

- 说“模块化单体”，不说“微服务架构”。
- 说“DDD 思想/模块化分层”，不说“严格 DDD”。
- 压测数据是本机可复现基线，不是生产 SLA，不声明未建立的 P95 门槛或容量上限。
- Kubernetes 仅是通过离线 schema 校验的部署草案，不说“生产级 Kubernetes”。
- 这是复杂有状态应用的全栈工程项目，不直接包装成 Agent 项目。投递 AI 全栈/Agent 岗位时，建议另外补一个真实使用模型调用、Tool Calling、RAG 或评测体系的小型项目。
