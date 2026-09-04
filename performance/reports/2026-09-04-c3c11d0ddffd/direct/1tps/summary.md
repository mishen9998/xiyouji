# k6 双实例多人开局业务链路报告

- 生成时间：2026-09-03T19:01:52.142Z
- 配置：`load`
- app-1：`http://localhost:18081`
- app-2：`http://localhost:18082`
- 时延门禁：未启用（通过环境变量显式设置）
- 本次阈值验收：**PASS**

> 这些数值只描述本次测试配置、数据与运行环境，不构成生产性能结论或 SLO。

## 本次结果

| 指标 | 数值 |
| --- | ---: |
| 完成迭代 | 181 |
| 实际迭代速率 | 1.00 iterations/s |
| HTTP 请求速率 | 14.07 req/s |
| 丢弃迭代 | 0 |
| 完整业务链路成功率 | 100.00% |
| 房间清理成功率 | 100.00% |
| 跨实例不一致 | 0.00%（0 次） |
| 非预期 409 | 0 |
| HTTP 429 | 0 |
| HTTP 5xx | 0 |
| 业务链路耗时 P95 | 117.00 ms |
| HTTP 请求耗时 P95 | 19.88 ms |
| HTTP 请求失败率 | 0.00% |

## 验收阈值

| 指标 | 条件 | 结果 |
| --- | --- | --- |
| `business_cleanup_success` | `rate>=0.99` | PASS |
| `business_flow_success` | `rate>=0.99` | PASS |
| `business_step_success` | `rate>=0.99` | PASS |
| `cross_instance_mismatch` | `rate==0` | PASS |
| `dropped_iterations` | `count==0` | PASS |
| `http_req_failed` | `rate<=0.01` | PASS |
| `rate_limited_responses` | `count==0` | PASS |
| `server_errors` | `count==0` | PASS |
| `unexpected_conflicts` | `count==0` | PASS |

## 覆盖链路

app-1 游客登录 → app-2 游客登录 → app-1 创建房间 → app-2 跨实例读取并加入 → 双方跨实例选角与准备 → app-1 开始游戏 → app-2 校验开局状态 → 房主解散房间。

## 数据清理边界

- “房间清理成功”表示房主退出后逻辑房间已解散；已完成的幂等记录由 Redis 在 10 分钟后自然过期。
- 若建房已在服务端完成、但响应在返回房间码前丢失，脚本无法定向解散该房间，需依赖房间 2 小时 TTL。
- Redis 的逻辑删除或 TTL 到期不代表 AOF/数据卷文件会立即缩小；物理空间回收取决于后续 AOF 重写等维护过程。
- 游客登录仅签发 JWT，本场景不会在 MySQL 中创建测试用户。
