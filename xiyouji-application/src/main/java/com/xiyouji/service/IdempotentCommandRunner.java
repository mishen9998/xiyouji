package com.xiyouji.service;

import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 幂等命令执行模板
 *
 * 封装 Controller 写端点的标准幂等流程：
 *   begin → 缓存命中重放 → 已完成状态回放 → 执行命令 → 记录响应
 * 执行失败时中断当前幂等标记并原样抛出，由全局异常处理器兜底。
 *
 * 原先该样板在 Game/Room/Battle 三个控制器中重复出现 20 余次，
 * 统一收拢后端点只保留：指纹与作用域计算、命令执行与完成态回放策略。
 */
@Component
public class IdempotentCommandRunner {

    private final CommandIdempotencyService idempotency;

    public IdempotentCommandRunner(CommandIdempotencyService idempotency) {
        this.idempotency = idempotency;
    }

    /**
     * 执行幂等命令并返回响应
     *
     * @param completedReplay 先前请求已"完成"但未缓存完整响应（旧标记兼容）时的回放策略，
     *                        通常基于当前状态重建响应；不会在缓存命中路径上被调用
     */
    public <T> T run(String scope, String idempotencyKey, String fingerprint,
                     Class<T> responseType,
                     Supplier<T> execute,
                     Function<IdempotencyStore.Entry, T> completedReplay) {
        IdempotencyStore.Entry previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        T cached = idempotency.replay(previous, responseType);
        if (cached != null) return cached;
        if (previous != null && previous.completed()) {
            return completedReplay.apply(previous);
        }
        try {
            T result = execute.get();
            idempotency.completeResponse(scope, idempotencyKey, fingerprint, result);
            return result;
        } catch (RuntimeException error) {
            idempotency.abort(scope, idempotencyKey);
            throw error;
        }
    }
}