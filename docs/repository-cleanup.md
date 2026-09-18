# 仓库整理记录

整理日期：2026-09-05。整理前功能基线：`89fa9fe`。

本次版本文件从 620 个整理为 520 个，内容大小由约 82.76 MiB 降为 71.39 MiB（不含 Git 历史和可再生成的依赖/构建缓存）。另外约 1.93 GiB 的本地临时与移除文件已移到项目外恢复目录，因此项目目录变小，但恢复副本仍占用本机磁盘。

## 目录归位

- 游戏图库由根目录 `插图/` 移入 `assets/images/`；同步 Docker、Maven 与 Vite 路径，浏览器 `/images/` 地址不变。
- 四份历史 HTML、五人联机规划与开发志 PPT 统一放入 `docs/archive/`。
- 云部署脚本迁入 `scripts/deploy-cloud.sh`，双实例启动器迁入 `scripts/start-cluster.bat`。
- 根目录仅保留“启动演示”和“停止演示”两个面向玩家的双击入口；启动演示已经包含构建更新。
- 用户已有 Word 文档仍位于 `需求文档/`，Office 编辑锁文件通过 Git 忽略规则排除。

## 移除依据

| 类别 | 清理内容与依据 |
| --- | --- |
| 旧启动入口 | 旧 Java/H2 启动器、VBS 包装器、旧机器绝对路径启动器与重复重建入口；现由演示脚本和 README 中的开发命令覆盖 |
| 调试脚本 | 根目录六个 `test-*.ps1` 与三个 `test_*.py` 缺少当前强制幂等键/状态版本头，不能作为现行验收；保留 Maven、Vitest、Playwright、Compose E2E 与 k6 |
| 旧运维入口 | `docker-manage.bat`、`run-sonar.bat`、`update-cloud.sh` 与固定容器名的旧故障演练脚本；保留 Compose 和 CI 中的现行流程 |
| 重复配置 | 根 `sql/init.sql` 被 Flyway 与 DataInitializer 取代；根 `prometheus/` 被 `monitoring/prometheus/` 取代；模块 `.dockerignore` 不属于实际构建上下文 |
| 一次性生成器 | 固定旧电脑路径的 `generate-ppt.js`、仅为它服务的根 `package.json`/锁文件及根 `node_modules/`；保留 PPT 成品 |
| 无引用图片 | `node_emperor.jpg`、`debuff_weak.jpg`；当前皇宫节点和状态图标使用 emoji |
| 重复图片 | 孙悟空、猪八戒、沙僧的三个 `avatar_*.jpg` 与立绘 SHA-256 相同；头像路径改为复用立绘 |
| 文档资源 | 移除 71 个未使用字体/许可证文件，以及三份字节相同的图表库副本，保留实际使用字体及其许可证 |
| 本地临时文件 | `tmp/` 中的便携办公工具、安装包、渲染检查等；`output/`、分享打包目录和 `query` 临时文本 |

已发布的性能报告、数据库迁移历史、运行配置、代码模块与有调用的组件均予以保留。真实 `.env` 密钥、Docker 数据卷和 IDE 个人配置不纳入提交。

## 恢复与验证

移出项目的文件在本机本次任务的 `work/xiyouji-cleanup-20260905/` 中保留恢复副本。已跟踪文件还可从上述 Git 基线恢复；不改写既有 Git 历史。

验证范围：前端生产构建、动态图片映射、Maven 完整验证与 MySQL/Redis 集成测试、Docker 演示构建/健康检查、浏览器关键路径和静态资源 HTTP 回读。历史 HTML 的本地依赖链接也进行存在性检查。

本地验收通过：9 个前端单元测试、79 个 Maven 测试、4 个 Playwright 场景、203 张图片 HTTP 回读、4 份历史 HTML 依赖和当前文档链接检查。MySQL/Redis 容器集成测试与五模块 JaCoCo 门禁均通过。

---

## 2026-09-18 目录结构与本地工作区规范化

整理日期：2026-09-18。整理前功能基线：`194da98`（v1.2.0）。

本轮整理只移动文档与本地工作区文件，不改动任何源码与构建逻辑；整理前后各执行一次回归（后端 Maven verify、前端类型检查/生产构建/单元测试、Compose 配置校验），结果一致。

### 目录归位

- 根目录的交付评估报告移入 `docs/项目部署交付评估与IDEA学习指南.txt`（git mv，保留历史）。
- 根目录 8 个 2026-09-06 发布会话遗留的 `tmp-*.log` 归档至 `tmp/archive/2026-09-06-release-logs/`（Git 忽略，确认不需要后可整目录删除）。
- 误初始化且与项目无关的流水线工作区 `pipeline-workspace/` 移入 `tmp/deprecated/pipeline-workspace/`（Git 忽略；确认不需要后可整目录删除）。

### 忽略规则规范化

- `.gitignore`：四个本地多 Agent 流水线目录（`multi-agent-doc-workflow/`、`pipeline-expedition-v2/`、`pipeline-game-polish/`、`pipeline-workspace/`）整体纳入忽略，替代原先仅忽略 `pipeline-game-polish/worktrees/` 的条目；`git status` 不再被本地工作区污染。
- `.dockerignore`：补充上述四个目录与 `.trae/`，避免约 2.1 GiB 的本地流水线证据与 IDE 文件进入 Docker 构建上下文。

### 保留说明

- `pipeline-expedition-v2/`（远征 V2 需求批次，状态阻塞等待授权）与 `pipeline-game-polish/`（上一批打磨会话；其 `final-acceptance.md` 记录了 t4 阻断缺陷 F1）保留在原地，供流水线续跑与缺陷追溯。
- `frontend-vue/e2e/T5-PERFORMANCE.md` 引用的 `pipeline-game-polish/tasks/t5` 性能证据路径保持有效。
- `output/release-20260906-rc1/`（466 MiB 发布候选包）与 `backups/` 演练产物继续由既有忽略规则覆盖，不属于版本库内容。
