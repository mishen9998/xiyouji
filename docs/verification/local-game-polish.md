# 本地游戏优化版：启动与复验

本文件是可复现操作说明，不是整体验收通过声明。最终结果以主工作区 `tasks/t6/result.md` 为准；等待上游修复时使用 `tasks/t6/dev-checkpoint.md`。不需要 SSH，不访问云服务器。

## 独立本地环境

要求 Docker Desktop、Java 17、Node 20.18.1。项目 Maven Wrapper 可用，避免依赖本机不完整的 Maven 安装。以下命令在仓库根目录运行；PowerShell 可直接执行。

```powershell
.\mvnw.cmd -B verify
docker compose -p xiyouji-t6-20260917 -f docker-compose.t6.yml up -d
docker compose -p xiyouji-t6-20260917 -f docker-compose.t6.yml ps
```

独立项目只启动 MySQL、Redis、两个应用实例；MySQL/Redis 不公开宿主机端口。应用仅绑定 `127.0.0.1:18086`、`127.0.0.1:18087`。配置中的数据库密码和 JWT 密钥仅用于这组可弃置的本地测试环境，不能用于正式部署。不要停止其他 Compose 项目、Dify、mold 或 kind 服务。

先等两应用容器均为 healthy，再打开前端。重新编译 JAR 后，已经运行的 JVM 不会自动载入新文件：

```powershell
docker compose -p xiyouji-t6-20260917 -f docker-compose.t6.yml restart app-1 app-2
docker compose -p xiyouji-t6-20260917 -f docker-compose.t6.yml ps
```

前端命令在 `frontend-vue` 目录运行。若本机不是 Node 20.18.1，以下 npm exec 显式选择对应运行时。`npm ci` 只在尚未安装依赖时执行。

```powershell
npm ci
npm exec --yes --package=node@20.18.1 -- node node_modules/vue-tsc/bin/vue-tsc.js --noEmit
npm exec --yes --package=node@20.18.1 -- node node_modules/vitest/vitest.mjs run
npm exec --yes --package=node@20.18.1 -- node node_modules/vite/bin/vite.js build
$env:VITE_API_TARGET='http://127.0.0.1:18086'
npm exec --yes --package=node@20.18.1 -- node node_modules/vite/bin/vite.js preview --host 127.0.0.1 --port 4176 --strictPort
```

本机演示入口 `http://127.0.0.1:4176`。可使用游客身份；单人从营地选角出发，联机从营地创建房间，另一浏览器窗口使用不同身份输入八位房间码。最多五名角色。此绑定只提供本机演示，手机宽度检查是 Chromium 模拟，不宣称手机真机远程访问已经测试。

停止时仅操作本项目，保留数据库卷：

```powershell
docker compose -p xiyouji-t6-20260917 -f docker-compose.t6.yml down
```

## 自动化复验入口

在仓库根目录执行真实双实例 REST/STOMP 测试：

```powershell
.\mvnw.cmd -B -Pcompose-e2e '-Dtest=DistributedComposeE2ETest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dxiyouji.e2e.app1=http://127.0.0.1:18086' '-Dxiyouji.e2e.app2=http://127.0.0.1:18087' '-Dxiyouji.e2e.origin=http://127.0.0.1:4176' test
```

生产预览保持运行，另开终端在 `frontend-vue` 执行。证据目录每轮换名，避免覆盖旧失败记录。下面 `test-results/local-*` 仅为重跑默认示例，交付证据已另存主工作区 `tasks/t6/evidence`。

```powershell
$env:PLAYWRIGHT_BASE_URL='http://127.0.0.1:4176'
npm exec --yes --package=node@20.18.1 -- node node_modules/@playwright/test/cli.js test e2e/t6-real-integration.spec.ts --workers=1 --retries=0 --output test-results/local-real
npm exec --yes --package=node@20.18.1 -- node node_modules/@playwright/test/cli.js test e2e/t5-experience.spec.ts e2e/t5-battle-viewport.spec.ts e2e/t4-recovery.spec.ts e2e/reward-confirmation.spec.ts e2e/t6-art-visual.spec.ts --workers=1 --retries=0 --output test-results/local-layout
npm exec --yes --package=node@20.18.1 -- node node_modules/@playwright/test/cli.js test e2e/t5-performance.spec.ts --workers=1 --retries=0 --output test-results/local-performance
```

额外手机宽度真实单人旅程：只给该命令设置 `T6_HOST_MOBILE=1`，之后移除，以免改变其他测量配置。

```powershell
$env:T6_HOST_MOBILE='1'
npm exec --yes --package=node@20.18.1 -- node node_modules/@playwright/test/cli.js test e2e/t6-real-integration.spec.ts --grep '1-player' --workers=1 --retries=0 --output test-results/local-real-mobile
Remove-Item Env:T6_HOST_MOBILE
```

## 证据边界

- `t6-real-integration.spec.ts` 使用真实游客 JWT、REST、STOMP、MySQL/Redis；浏览器只改写目标实例，不伪造响应。为可重复穷举，直接在新建的隔离 Redis 存档里固定三层各27行合法路径、9事件、12敌人，并给予加速用生命、金币和零费高伤害牌。它验证服务流程、跨端导航和结算，不证明常规难度、随机分布或原速通关时长。每个敌人至少实际结算一回合，完整行动循环由后端测试验证。
- `t5-experience`、`t5-battle-viewport` 与 `t6-art-visual` 的画面夹具检验视口、触控、字体、插画和控件可达性，不能当作真实业务通关。真实全流程证据单列。
- `t5-performance` 分别测加载和稳态。rAF 间隔是浏览器帧调度代理，点击到 DOM/两次 rAF 是模拟视觉反馈；CPU4×/4Mbps/150ms 是桌面 Chromium 模拟，不是真机数据、GPU测量、后端延迟或线上 SLA。
- LCP 保留每次候选 `lcpEntries` 和完整资源/CDP传输量。身份页最终候选可能是 `p.privacy-note` 文本，不能用该数字声称背景图加载更快；关键图体积另行统计。
- 浏览器 `real-journey.json`、`partial-progress.json`、`real-recovery.json` 直接写入输出目录，即使使用 list reporter 也保留。截图与日志可以交叉核对；历史失败记录不删除。

## 素材与原始需求索引

| 需求 | 实现 / 可复验入口 |
|---|---|
| 九张真实生成素材，第一张为风格锚 | `docs/art/t6-scene-prompts.md`、`t6-boss-prompts.md`：完整生成提示词、原路径和参考说明；源图 `assets/images/旅程/*.png` |
| 背景无字、三 Boss 真透明、旧插画保留 | 人工看图 + `frontend-vue/scripts/verify-art.mjs`：九源哈希、尺寸、RGBA透明比例、派生alpha；旧源的 git diff 检查 |
| 按场景加载、手机/桌面图、失败 fallback | `scene-images.json` + `image-manifest.json`、`ResponsiveImage.vue`、`t6-art-visual.spec.ts` |
| 通关体验和权威状态恢复 | `CompletionView.vue` / `CompletionView.test.ts`；真实旅程最后刷新恢复并检查通关图成功解码 |
| 七种敌人行动、格挡/中毒/力量、预告锁定 | `EnemyTurnResolverTest`、`CombatModeParityTest`；真实旅程12敌人实际回合 |
| 十二循环、三层70:30遭遇、旧快照兼容 | `EncounterCatalogTest`、`LegacyEnemySnapshotTest`、`HibernateMappingIntegrationTest`；仅追加V5迁移 |
| 九事件、随机目标锁定、原子费用收益、商店校验 | `EventEngineTest`、`EventPersistenceAndAtomicityTest`、`EventBattleBridgeTest`、`ShopSafetyTest`；真实单人/五人选项1、双人选项2 |
| 可跳过出发、章节、Boss及结局故事 | `StoryCatalogTest`；真实旅程跳过不等待，结局DOM可即刻离开 |
| 成员权限、幂等安全失败、创建原子性 | `RoomAuthorizationTest`、`JoinReplayAuthorizationTest`、`IdempotencyRedisIntegrationTest`、`DistributedInfrastructureContainersTest` |
| 双实例、容量≤5、真实漏消息重连收敛 | `DistributedComposeE2ETest` + `t6-real-integration.spec.ts` 的 recovery 案例 |
| 单人/双人/五人三层各27行、奖励及通关 | `t6-real-integration.spec.ts` 三个真实旅程，各81个UI节点；五人使用五个不同角色 |
| 360/390/768/1366/1440、横屏、200%真字体 | `t5-experience.spec.ts`、`t5-battle-viewport.spec.ts`、`t6-art-visual.spec.ts` |
| 手机点牌确认、键盘、44/48触控、地图resize | 前述视口测试、组件单测；观察五人预告与底栏仍可达 |
| 轻量插画、减少动画、隐藏暂停、无WebGL回退 | `t5-experience.spec.ts` 及相应组件单测；3D资源只由主动选择加载 |
| 最终性能预算与前后截图 | `t5-performance.spec.ts`，完整原始JSON；主工作区 `tasks/t6/evidence/comparison` 同视口前后图 |
| 五Maven模块/ArchUnit/测试/构建 | Maven `verify`、`ArchitectureTest`；Node20.18.1 typecheck、Vitest、Vite生产构建 |
| 无云修改、V1–V4不变、原技术栈与章节 | 只操作上述本地Compose；git diff校核V1–V4，三层与五角色由真实旅程断言 |

最终结果报告会把每条索引链接到实际执行轮次与通过/未覆盖状态；此索引不把“有测试文件”当作“最终测试已通过”。
