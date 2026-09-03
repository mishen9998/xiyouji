# 性能验证指南

本目录用于保存可复现的性能验证脚本与报告。当前的核心脚本是
[`k6/business-flow.js`](k6/business-flow.js)，它验证双实例多人开局链路，而不是用健康检查冒充业务压测。

性能验证分成两类，结论必须分开表述：

1. **双实例直连业务基线**：分别访问 `app-1:18081` 和 `app-2:18082`，绕过 Nginx，定位应用、MySQL 与 Redis 共享状态链路的表现。
2. **Nginx 限流内低速 E2E**：所有请求访问 `localhost:8080`，以明显低于单 IP `10 r/s` 的速率校验反向代理、负载均衡和共享状态，不用于推导后端容量。

> 首次运行的延迟值只是观测结果。没有稳定的重复基线前，不应写“P95 小于 500 ms”，也不应把本机结果称为生产 SLO 或生产容量。

## 被测业务事务

一次完整业务事务包含以下 **12 个业务请求**：

1. 在 app-1 获取房主游客 JWT。
2. 在 app-2 获取玩家游客 JWT。
3. 房主在 app-1 创建房间。
4. 玩家从 app-2 跨实例读取该房间，并核对 `code` 与 `stateVersion`。
5. 玩家在 app-2 加入房间。
6. 房主在 app-1 选择 `SUN_WUKONG`。
7. 玩家在 app-2 选择 `ZHU_BAJIE`。
8. 房主在 app-1 准备。
9. 玩家在 app-2 准备。
10. 房主从 app-1 查询 `canStart`。
11. 房主在 app-1 开始游戏；该步骤会读取 MySQL 中的角色和卡牌目录。
12. 玩家从 app-2 跨实例读取开局状态，并核对人数、状态、地图与版本。

正常情况下，这 12 个业务请求之后，`finally` 清理阶段还会发送最多 2 个请求：读取最新房间版本、由房主退出并解散房间。若两次游客登录随机得到同一用户名，脚本会最多重试玩家登录 2 次；因此一次迭代通常有 14 个、极端情况下最多 16 个 `/api/**` 请求。Nginx 限流预算必须按这个上界计算。

每个变更命令都使用唯一的 `X-Idempotency-Key`；需要并发控制的命令使用上一响应返回的 `X-Expected-State-Version`。版本没有递增、跨实例状态不可见或响应结构不符合约定时，相应 `business_step_success` 和整条 `business_flow_success` 都会失败。

这条链路使用 `/api/auth/**` 和 `/api/room/**`。应用内的 `RateLimitFilter` 只限制 `/api/game/**`（30 请求/秒/IP），因此不会干扰直连房间基线。房间创建当前使用全局分布式锁，若它在阶梯测试中成为瓶颈，应作为真实的设计取舍和后续优化项记录。

### 数据生命周期与清理边界

- `/api/auth/guest` 不写入 MySQL，因此当前脚本不会制造永久用户数据。
- 房间保存为 Redis `room:<code>`，每次保存刷新 2 小时 TTL。正常情况下，`finally` 会先读取最新版本，再由房主定向退出并删除房间。
- 每次正常成功迭代约产生 10 个 `xiyouji:idempotency:*` 键；若游客名碰撞并重试还会增加。它们保留约 10 分钟后过期，因此“房间清理成功”不等于 Redis 立即回到运行前的键数量。
- 若创建房间已在服务端提交、但客户端在收到房间码前断开，脚本无法定向删除该房间，它可能保留到 2 小时 TTL 到期。中断或失败运行必须记录残留键数量。
- Redis 开启 AOF；逻辑删除或 TTL 到期不意味着数据卷文件立即缩小。正式重复实验若要求完全相同的初始状态，应销毁并重建**独立性能项目**的数据卷。
- 当前没有专用批量测试数据清理端点。若将来把游客登录替换成 `/api/auth/register`，`users` 表会永久增长，只能在隔离数据库使用唯一用户名，并设计单独、受控的事后清理流程。

失败或中断后，可在销毁独立数据卷前记录残留数量：

```powershell
$redisContainer = docker ps `
  --filter "label=com.docker.compose.project=$project" `
  --filter 'label=com.docker.compose.service=redis' `
  --format '{{.ID}}'
if (-not $redisContainer) { throw '没有找到性能项目的 Redis 容器' }

$roomKeys = @(docker exec $redisContainer redis-cli --scan --pattern 'room:*')
$idempotencyKeys = @(docker exec $redisContainer redis-cli --scan --pattern 'xiyouji:idempotency:*')
"room_keys=$($roomKeys.Count) idempotency_keys=$($idempotencyKeys.Count)"
```

## 前置条件

- Docker Desktop、Docker Compose、Java 构建依赖及 k6 已安装。
- 从仓库根目录执行下列 PowerShell 命令。
- 使用未提交的 `.env` 提供 `DB_PASSWORD`、`JWT_SECRET`、`GRAFANA_ADMIN_PASSWORD` 等值；不要把真实密钥写进命令、截图或报告。
- `handleSummary` 不会创建目录，必须先创建报告目录。

建议为压测使用独立 Compose 项目，避免影响日常开发数据：

```powershell
$project = 'xiyouji-perf'
$shortSha = (git rev-parse --short=12 HEAD).Trim()
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDir = "performance/reports/$stamp-$shortSha"

New-Item -ItemType Directory -Force -Path `
  "$runDir/smoke", `
  "$runDir/warmup", `
  "$runDir/direct", `
  "$runDir/confirmation", `
  "$runDir/nginx", `
  "$runDir/resources", `
  "$runDir/logs" | Out-Null

docker compose -p $project -f docker-compose.yml -f docker-compose.e2e.yml up -d --build --wait
Invoke-RestMethod http://localhost:18081/actuator/health/readiness
Invoke-RestMethod http://localhost:18082/actuator/health/readiness
```

正式测试前先静置 60 秒。若需要完全干净的数据卷，只能删除这个已核对过的独立性能项目：

```powershell
docker ps -a --filter "label=com.docker.compose.project=$project"
docker volume ls --filter "label=com.docker.compose.project=$project"

# 确认上面的目标确实都属于 xiyouji-perf 后再执行。
docker compose -p $project -f docker-compose.yml -f docker-compose.e2e.yml down -v --remove-orphans
docker compose -p $project -f docker-compose.yml -f docker-compose.e2e.yml up -d --build --wait
```

## 环境变量

`business-flow.js` 当前支持以下参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `APP_1_URL` | `http://host.docker.internal:18081` | 第一逻辑实例地址；宿主机运行 k6 时应显式设为 `http://localhost:18081` |
| `APP_2_URL` | `http://host.docker.internal:18082` | 第二逻辑实例地址 |
| `PROFILE` | `smoke` | `smoke` 或 `load` |
| `REQUEST_TIMEOUT` | `15s` | 单个 HTTP 请求超时 |
| `ITERATION_PAUSE_SECONDS` | `0.1` | 每次业务事务结束后的暂停秒数 |
| `VUS` / `ITERATIONS` | `1` / `1` | `smoke` 配置的虚拟用户与总迭代数 |
| `MAX_DURATION` | `2m` | `smoke` 最大运行时间 |
| `RATE` / `TIME_UNIT` | `1` / `1s` | `load` 的目标事务到达率 |
| `DURATION` | `30s` | `load` 持续时间 |
| `PRE_ALLOCATED_VUS` / `MAX_VUS` | `5` / `20` | 到达率执行器的预分配与最大 VU |
| `FLOW_SUCCESS_TARGET` | `0.99` | 完整链路及清理成功率下限 |
| `STEP_SUCCESS_TARGET` | `0.99` | 所有业务步骤成功率下限 |
| `HTTP_FAILURE_TARGET` | `0.01` | HTTP 失败率上限 |
| `HTTP_P95_MS` | 未设置 | 可选的 HTTP P95 门禁（毫秒）；只有存在证据支持的目标时才设置 |
| `FLOW_P95_MS` | 未设置 | 可选的业务事务 P95 门禁（毫秒）；只有存在证据支持的目标时才设置 |
| `SUMMARY_JSON` / `SUMMARY_MD` | 当前目录下默认文件名 | 结构化摘要与 Markdown 摘要路径 |

脚本输出的关键自定义指标为：

- `business_flow_success`：12 个业务步骤和最终清理均成功才记为成功。
- `business_step_success`：逐步骤 HTTP 状态、JSON 结构和业务约定是否正确。
- `business_cleanup_success`：房间是否已不存在或成功由房主解散。
- `cross_instance_mismatch`：两个明确的跨实例读取检查点出现状态不一致的比率，固定要求为 0。
- `unexpected_conflicts`：非预期 HTTP 409 计数，固定要求为 0。
- `rate_limited_responses`：HTTP 429 计数，固定要求为 0。
- `server_errors`：HTTP 5xx 计数，固定要求为 0。
- `business_flow_duration`：含清理阶段的整条迭代耗时。
- `business_step_duration`：按 `business_step`、`target_instance` 等标签记录的单步骤耗时。
- `business_flow_failures`：失败链路计数。

首次探索不使用拍脑袋的延迟门槛。下面的命令有意不传 `HTTP_P95_MS` 和 `FLOW_P95_MS`；真正的延迟回归线应在三次稳定运行后根据基线建立。请求本身仍受 `REQUEST_TIMEOUT` 保护。

## 1. Smoke：先验证脚本和数据清理

Smoke 只运行一条链路，正确性要求为 100%。它不能形成性能结论。

```powershell
k6 run `
  --out "json=$runDir/smoke/metrics.ndjson" `
  -e PROFILE=smoke `
  -e VUS=1 `
  -e ITERATIONS=1 `
  -e APP_1_URL=http://localhost:18081 `
  -e APP_2_URL=http://localhost:18082 `
  -e FLOW_SUCCESS_TARGET=1 `
  -e STEP_SUCCESS_TARGET=1 `
  -e HTTP_FAILURE_TARGET=0 `
  -e "SUMMARY_JSON=$runDir/smoke/summary.json" `
  -e "SUMMARY_MD=$runDir/smoke/summary.md" `
  performance/k6/business-flow.js
```

继续前应确认：链路成功率、步骤成功率、清理成功率均为 100%，没有遗留测试房间。

## 2. 预热：不计入正式结果

预热用于稳定 JIT、连接池、MySQL 缓存和 Redis 序列化路径。预热结果保留用于审计，但不能混入正式性能汇总。

```powershell
k6 run `
  -e PROFILE=load `
  -e RATE=1 `
  -e TIME_UNIT=1s `
  -e DURATION=3m `
  -e PRE_ALLOCATED_VUS=8 `
  -e MAX_VUS=32 `
  -e APP_1_URL=http://localhost:18081 `
  -e APP_2_URL=http://localhost:18082 `
  -e ITERATION_PAUSE_SECONDS=0 `
  -e FLOW_SUCCESS_TARGET=0.99 `
  -e STEP_SUCCESS_TARGET=0.99 `
  -e HTTP_FAILURE_TARGET=0.01 `
  -e "SUMMARY_JSON=$runDir/warmup/summary.json" `
  -e "SUMMARY_MD=$runDir/warmup/summary.md" `
  performance/k6/business-flow.js

Start-Sleep -Seconds 30
```

若预热出现状态版本错误、429、5xx、清理失败或持续资源饱和，不应直接进入正式阶梯。

## 3. 双实例直连阶梯基线

直连基线使用 `18081` 和 `18082`，不经过 Nginx。建议按 1、2、4、6、8、12 transaction/s 分档，每档独立运行 3 分钟，档间冷却 30 秒。分档执行比把所有阶段合并成一个摘要更容易定位拐点。

```powershell
$rates = 1, 2, 4, 6, 8, 12

foreach ($rate in $rates) {
  $stageDir = "$runDir/direct/${rate}tps"
  New-Item -ItemType Directory -Force -Path $stageDir | Out-Null

  k6 run `
    --out "json=$stageDir/metrics.ndjson" `
    -e PROFILE=load `
    -e "RATE=$rate" `
    -e TIME_UNIT=1s `
    -e DURATION=3m `
    -e PRE_ALLOCATED_VUS=32 `
    -e MAX_VUS=128 `
    -e APP_1_URL=http://localhost:18081 `
    -e APP_2_URL=http://localhost:18082 `
    -e ITERATION_PAUSE_SECONDS=0 `
    -e FLOW_SUCCESS_TARGET=0.99 `
    -e STEP_SUCCESS_TARGET=0.99 `
    -e HTTP_FAILURE_TARGET=0.01 `
    -e "SUMMARY_JSON=$stageDir/summary.json" `
    -e "SUMMARY_MD=$stageDir/summary.md" `
    performance/k6/business-flow.js

  if ($LASTEXITCODE -ne 0) {
    Write-Warning "${rate} transaction/s 未通过；先分析结果，不要盲目继续加压。"
    break
  }
  Start-Sleep -Seconds 30
}
```

这里的 `RATE` 是**完整业务事务/秒**，不是 HTTP QPS，也不是并发用户数。每笔事务通常有 12 个业务请求和最多 2 个清理请求，游客名碰撞时还可能增加登录重试；报告应同时展示目标 transaction/s、实际完成 transaction/s、实际 HTTP req/s 和活动 VU。

一个档位只有同时满足以下条件，才能称为“最高已验证稳定档”，不能称为“系统最大容量”：

- 实际完成速率至少达到目标的 98%。
- `dropped_iterations` 为 0。
- `business_flow_success`、`business_step_success` 达到门槛，且清理成功。
- `unexpected_conflicts`、`rate_limited_responses`、`server_errors` 和 `cross_instance_mismatch` 均为 0；这些指标已有独立硬门禁，即使总成功率仍高于 99%，出现一次也不得视为干净通过。
- P95 在该档运行窗口内没有持续上升，而不是只看全程聚合值。
- app-1、app-2、MySQL、Redis 的 CPU、内存、GC 与连接池没有持续饱和。

若连续两个档位出现明显饱和或错误，应停止探索。阶梯最高只跑到 12 transaction/s 时，结论只能写“验证到 12 transaction/s”，不能写“最大容量为 12 transaction/s”。

## 4. 30 分钟确认与重复性

选择最高已验证稳定档的约 70%，向下取整为脚本支持的整数 `RATE`，持续 30 分钟。至少完整重复 3 次并报告三次结果和中位数，不能只挑最好的一次。

```powershell
# 手工替换为上一节真实通过的最高档位。
$verifiedRate = 0
if ($verifiedRate -le 0) { throw '请先填写真实的最高已验证稳定档位' }

$confirmRate = [Math]::Max(1, [Math]::Floor($verifiedRate * 0.7))

1..3 | ForEach-Object {
  $attempt = $_
  $attemptDir = "$runDir/confirmation/run-$attempt"
  New-Item -ItemType Directory -Force -Path $attemptDir | Out-Null

  k6 run `
    --out "json=$attemptDir/metrics.ndjson" `
    -e PROFILE=load `
    -e "RATE=$confirmRate" `
    -e TIME_UNIT=1s `
    -e DURATION=30m `
    -e PRE_ALLOCATED_VUS=32 `
    -e MAX_VUS=128 `
    -e APP_1_URL=http://localhost:18081 `
    -e APP_2_URL=http://localhost:18082 `
    -e ITERATION_PAUSE_SECONDS=0 `
    -e FLOW_SUCCESS_TARGET=1 `
    -e STEP_SUCCESS_TARGET=1 `
    -e HTTP_FAILURE_TARGET=0 `
    -e "SUMMARY_JSON=$attemptDir/summary.json" `
    -e "SUMMARY_MD=$attemptDir/summary.md" `
    performance/k6/business-flow.js

  if ($LASTEXITCODE -ne 0) { throw "确认运行 $attempt 未通过" }
  Start-Sleep -Seconds 300
}
```

首次报告应如实列出观测到的 p50、p90、p95、p99 和 max，不把目标或阈值配置当成结果。获得三次稳定基线后，可将“三次 P95 中位数 × 1.25”作为后续版本的**回归告警线**，再通过 `HTTP_P95_MS` 和 `FLOW_P95_MS` 显式启用；它仍不是产品 SLO。

## 5. Nginx 限流内低速 E2E

Nginx 对同一来源 IP 的 `/api/**` 配置为 `10 r/s`，允许 `burst=20` 且 `nodelay`。宿主机 k6 的所有请求通常共享一个来源 IP，直接用高 VU 压 `8080` 只会测出 429 限流，不代表后端性能。

下面把两个逻辑地址都指向 Nginx，每 4 秒只启动 1 笔事务，且最多使用 1 个 VU。一次迭代通常有 14 个、游客名碰撞重试时最多 16 个受限请求，仍小于 burst 20；名义平均流量约为 3.5 HTTP req/s，极端重试情况下约为 4 HTTP req/s，明显低于 10 r/s。最终必须用 k6 的实际 `http_reqs / duration` 核对速率，并要求没有 429。

```powershell
$nginxStartUtc = (Get-Date).ToUniversalTime().ToString('o')

k6 run `
  --out "json=$runDir/nginx/metrics.ndjson" `
  -e PROFILE=load `
  -e RATE=1 `
  -e TIME_UNIT=4s `
  -e DURATION=10m `
  -e PRE_ALLOCATED_VUS=1 `
  -e MAX_VUS=1 `
  -e APP_1_URL=http://localhost:8080 `
  -e APP_2_URL=http://localhost:8080 `
  -e ITERATION_PAUSE_SECONDS=0 `
  -e FLOW_SUCCESS_TARGET=1 `
  -e STEP_SUCCESS_TARGET=1 `
  -e HTTP_FAILURE_TARGET=0 `
  -e "SUMMARY_JSON=$runDir/nginx/summary.json" `
  -e "SUMMARY_MD=$runDir/nginx/summary.md" `
  performance/k6/business-flow.js

$nginxContainer = docker ps `
  --filter "label=com.docker.compose.project=$project" `
  --filter 'label=com.docker.compose.service=nginx' `
  --format '{{.ID}}'
docker logs --since $nginxStartUtc $nginxContainer 2>&1 |
  Set-Content -LiteralPath "$runDir/logs/nginx-e2e.log"
```

在此模式下，脚本里的 `target_instance=app-1/app-2` 只是业务步骤的**逻辑槽位**，不能证明 Nginx 实际选择了哪个上游。必须结合 Nginx access log 的 `upstream_addr`，或额外访问不占游戏 API 限流配额的 `/api/instance/info`，确认两个实例都被命中：

```powershell
1..20 | ForEach-Object {
  (Invoke-RestMethod http://localhost:8080/api/instance/info).instanceId
} | Group-Object | Sort-Object Name
```

Nginx E2E 的验收条件是：429 与 5xx 为 0、业务与清理全部成功、跨实例状态校验通过、`dropped_iterations` 为 0、两个上游均有命中。这里的 P95 只能表述为“限流内低速端到端延迟”，不能用来推导后端吞吐、并发用户数或生产 SLO。

## 环境与资源采集

每份正式报告至少固定以下信息：

- 本地与 UTC 起止时间、Git SHA、分支、工作区是否干净。
- Windows/WSL/Docker 操作系统、CPU、内存，以及 Docker Engine 实际分配。
- Docker、Compose、k6、JDK/JRE 版本。
- 所有被测镜像的 ID/digest，而不只是可变 tag。
- 容器资源限制、JVM 参数、Hikari/Redis 连接池大小、追踪采样率、Prometheus 抓取间隔。
- app-1、app-2、MySQL、Redis、Nginx 每 5 秒的 CPU、内存、网络和块 I/O。
- JVM heap/GC、进程 CPU、HTTP 吞吐和延迟、Hikari active/pending，以及正式测试窗口。

当前仓库配置对 app-1/app-2 各限制 1 CPU/768 MiB，对 MySQL 限制 1 CPU/768 MiB，对 Redis 限制 0.5 CPU/384 MiB；Nginx 和观测组件没有单独资源上限。Redis 还配置了 256 MiB `maxmemory` 与 `noeviction`。这些是**配置事实**，正式报告仍应通过 `docker inspect` 记录当次实际生效值。

以下 PowerShell 模板只采集非敏感环境信息：

```powershell
$environmentFile = "$runDir/environment.txt"
@(
  "captured_at_utc=$((Get-Date).ToUniversalTime().ToString('o'))"
  "git_sha=$(git rev-parse HEAD)"
  "git_branch=$(git branch --show-current)"
  "git_status_begin"
  (git status --short)
  "git_status_end"
  "docker_version=$(docker version --format 'client={{.Client.Version}} server={{.Server.Version}}')"
  "compose_version=$(docker compose version --short)"
  "k6_version=$(k6 version)"
  "docker_engine=$(docker info --format 'cpus={{.NCPU}} memory_bytes={{.MemTotal}} driver={{.Driver}} cgroup={{.CgroupVersion}}')"
) | Set-Content -LiteralPath $environmentFile

Get-CimInstance Win32_OperatingSystem |
  Select-Object Caption,Version,OSArchitecture,TotalVisibleMemorySize |
  Format-List | Out-String | Add-Content -LiteralPath $environmentFile
Get-CimInstance Win32_Processor |
  Select-Object Name,NumberOfCores,NumberOfLogicalProcessors,MaxClockSpeed |
  Format-List | Out-String | Add-Content -LiteralPath $environmentFile

$containerIds = docker ps --filter "label=com.docker.compose.project=$project" --format '{{.ID}}'
if (-not $containerIds) { throw '没有找到性能项目容器' }
foreach ($containerId in $containerIds) {
  docker inspect $containerId --format `
    '{{index .Config.Labels "com.docker.compose.service"}}|image={{.Image}}|cpus={{.HostConfig.NanoCpus}}|memory={{.HostConfig.Memory}}' |
    Add-Content -LiteralPath $environmentFile
}
```

资源采样建议在另一 PowerShell 窗口运行，测试结束后按 `Ctrl+C` 停止：

```powershell
$project = 'xiyouji-perf'
$runDir = '替换为本次报告目录'
$statsFile = "$runDir/resources/docker-stats.ndjson"

while ($true) {
  $capturedAt = (Get-Date).ToUniversalTime().ToString('o')
  $containerIds = docker ps --filter "label=com.docker.compose.project=$project" --format '{{.ID}}'
  if (-not $containerIds) { throw '没有找到性能项目容器' }
  docker stats --no-stream --format '{{json .}}' $containerIds |
    ForEach-Object { "$capturedAt`t$_" } |
    Add-Content -LiteralPath $statsFile
  Start-Sleep -Seconds 5
}
```

不要把已展开的 `docker compose config` 原样写入报告，因为其中可能包含数据库密码和 JWT 密钥。若必须保留合并后的配置，请使用不展开变量的模式或先做可靠脱敏；报告中不得出现 `.env`、JWT、密码或游客 token。

## 报告目录建议

```text
performance/reports/<yyyyMMdd-HHmmss>-<short-sha>/
├── report.md
├── environment.txt
├── commands.txt
├── smoke/
│   ├── summary.json
│   ├── summary.md
│   └── metrics.ndjson
├── warmup/
│   ├── summary.json
│   └── summary.md
├── direct/
│   └── <rate>tps/
│       ├── summary.json
│       ├── summary.md
│       └── metrics.ndjson
├── confirmation/
│   └── run-<n>/
│       ├── summary.json
│       ├── summary.md
│       └── metrics.ndjson
├── nginx/
│   ├── summary.json
│   ├── summary.md
│   └── metrics.ndjson
├── resources/
│   ├── docker-stats.ndjson
│   └── prometheus-queries/
├── logs/
│   └── nginx-e2e.log
└── SHA256SUMS
```

`report.md` 应分别展示每个直连档位与 Nginx 低速校验，不应只给一个跨阶段聚合 P95。建议包含：

- 环境和资源限制。
- 精确命令、Git SHA、镜像 digest、起止时间。
- 每档目标/实际 transaction/s、HTTP req/s、活动 VU、迭代数与 dropped iterations。
- 业务成功、步骤成功、清理成功、HTTP 失败和状态码分布。
- 按业务步骤与逻辑实例拆分的 p50/p90/p95/p99/max。
- app-1/app-2/MySQL/Redis/Nginx 的资源曲线。
- 三次确认运行的逐次结果、中位数和离散程度。
- 所有异常、无效运行与重跑原因；不得删除失败结果只保留最好数据。

## 结论边界与简历口径

报告必须披露以下边界：

- 这是单机本地 Docker 测试，压测器和被测服务共享物理机；不包含公网、TLS、CDN、跨主机网络或 Kubernetes。
- 直连基线绕过 Nginx；Nginx 仅在单 IP 10 r/s 限流内做低速 E2E，两者不能混成一个容量结论。
- 工作负载覆盖双人房间、共享状态、选角、准备和开局，不代表所有战斗、卡牌和地图操作分布。
- 本轮不测大量 WebSocket 长连接、断网重连或消息风暴；已有功能 E2E 也不能替代 WebSocket 容量测试。
- 游客认证不会执行注册写库或 bcrypt 密码校验；开局步骤包含 MySQL 角色和卡牌目录读取。
- 数据集较小且经过预热，不代表冷缓存或海量历史数据场景；地图生成存在随机性。
- Nginx 和观测容器当前没有资源上限，默认链路追踪采样及 Prometheus 抓取会产生开销。
- 所有结果只对报告中的 commit、镜像、机器、资源配置、数据和日期有效。
- 基于基线的回归告警线不是生产 SLO，transaction/s 也不是“并发用户数”。

报告完成前可使用的诚实表述：

> 设计分层 k6 性能验证：绕过 Nginx 单 IP 10 r/s 限流，对双实例跨实例房间业务链路建立直连基线，并以限流内低速流量校验 Nginx 路由与共享状态一致性，留存环境、原始结果和资源曲线。

报告完成后，只有真实数字才能填入以下模板：

> 在固定本机 Docker 环境（双实例各 1 CPU/768 MiB）下，对 12 请求的跨实例双人房间事务以 **X transaction/s** 持续 30 分钟，完成 **N** 笔，失败率 **A%**、事务 P95 **B ms**、状态不一致 **0**；另以实测约 **C HTTP req/s**（低于 Nginx 单 IP 10 r/s 限流）完成低速端到端校验。

不要把尚未执行的命令、阈值配置或预期目标写成实测结果，也不要使用“生产级性能”“支持 X 并发用户”或没有报告支撑的 P95 数字。
