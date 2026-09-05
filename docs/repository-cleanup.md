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
