# 数据备份和隔离恢复

`scripts/backup_restore.py` 面向 `docker-compose-cloud.yml` 的单实例栈，需要 Python 3.9+ 和 Docker Compose v2。Linux 使用 `python3`，Windows 可使用 `python`。

## 备份

```bash
python3 scripts/backup_restore.py backup \
  --project xiyouji-cloud --env-file .env.cloud \
  --directory backups/2026-09-06-001
```

脚本暂时停止应用写入，依次导出 MySQL 和 Redis RDB，然后重新启动应用并等待健康检查。过程中游戏会短暂不可用，请在维护窗口执行。该一致性前提是只有此栈的 app 写入两个数据库；不适用于共享数据库上还有其他写入者的场景。

备份目录必须不存在；成功后包含 `mysql.sql`、`dump.rdb` 和校验清单 `manifest.json`。记录应用的确切镜像 ID；不导出密码或 JWT 密钥。失败时保留文件供排查，不视为有效备份。异常处理会尝试重启应用；若主机断电或脚本被强制终止，恢复主机后人工检查 app 状态。

Linux 目录和文件使用 700/600 权限；Windows 需另外通过 ACL 限制访问。备份中包含账户哈希及游戏数据，必须复制到受限、加密的异机存储；本机同一磁盘不构成灾难恢复保障。环境密钥单独保管，恢复原令牌需要原 JWT 密钥。

建议从每日备份、保留 7 个日备份及 4 个周备份开始制定策略；这只是待部署时确定的方案，当前脚本不会自动调度、上传或清除旧备份。正式接入宝塔计划任务前先测量维护窗口耗时。

## 恢复到全新隔离环境

在目标机器预先载入原镜像（使用固定 digest 拉取，或 `docker save/load` 传输）。准备外部 env 文件，然后执行：

```bash
python3 scripts/backup_restore.py restore \
  --project xiyouji-restore-drill-001 --env-file .env.cloud \
  --directory backups/2026-09-06-001 --port 18090
```

目标项目名必须以 `xiyouji-restore-` 开头；已有容器、卷或网络会被拒绝，避免覆盖现有业务。恢复前检查备份哈希，使用清单中的原镜像。先向新 Redis 卷复制 RDB，以 AOF 关闭模式加载快照，再启用 AOF 并等待重写完成，最后按正常 AOF 配置重建 Redis 容器。直接以 appendonly=yes 启动新卷会忽略独立 RDB，脚本显式处理了这个边界。

恢复后需验证原账户登录、原会话状态、房间回读，以及再次重启 Redis 后的数据状态。Redis TTL 是绝对过期时间，恢复不会延长原会话寿命；过期会话消失是预期行为。短期幂等记录及锁也可能在快照中，需等待残余 TTL 后再操作。

确认隔离恢复成功后，才安排代理切换或正式恢复窗口。脚本不会修改原栈、自动切流或删除失败恢复的数据。生产恢复和回退应记录实际恢复时间与恢复到的数据时刻。

## 升级与回退

可重复的业务恢复演练入口（只允许 `xiyouji-backup-*` 测试源项目，会创建测试账户和会话）：

```bash
python3 scripts/verify-backup-restore.py --env-file .env.demo \
  --source-project xiyouji-backup-source --source-port 18091 --target-port 18090
```

需先在 18091 启动独立 compact 测试栈，18090 必须空闲。验证完成后保留备份和恢复容器供检查，脚本不自动清理；结果保存在备份目录的 `verification.json`。

升级前备份并记录镜像 digest、提交号和 Flyway 版本。先用隔离恢复环境验证新镜像。数据库或 Redis 数据结构不兼容时，仅切回旧镜像不足以回退；应使用升级前快照恢复到新栈，并明确会损失快照之后的写入，再安排切流。
