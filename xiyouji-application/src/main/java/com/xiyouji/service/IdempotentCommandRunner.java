package com.xiyouji.service;

import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 幂等命令执行模板
 *
 * 封装 Controller 写端点的标准幂等流程：
 *   begin → 缓存命中重放 → 已完成状态回放 → 执行命令 → 记录响应
 * 进入业务后失败保留标记，未知结果只通过同命令回执恢复。
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
        return run(scope, idempotencyKey, fingerprint, responseType, () -> { }, execute, completedReplay);
    }

    /** Admission commands may require different authorization for first execution and replay. */
    public <T> T run(String scope, String idempotencyKey, String fingerprint,
                     Class<T> responseType, Runnable authorizeReplay,
                     Supplier<T> execute,
                     Function<IdempotencyStore.Entry, T> completedReplay) {
        IdempotencyStore.Entry previous = idempotency.begin(scope, idempotencyKey, fingerprint);
        // Authorize before decoding a cached body or rebuilding a legacy completed response.
        if (previous != null && previous.completed()) authorizeReplay.run();
        T cached = idempotency.replay(previous, responseType);
        if (cached != null) return cached;
        if (previous != null && previous.completed()) {
            return completedReplay.apply(previous);
        }
        try {
            T result = execute.get();
            try { idempotency.completeResponse(scope, idempotencyKey, previous, result); }
            catch (RuntimeException failure) { throw new com.xiyouji.exception.ResultUnknownException(); }
            return result;
        } catch (RuntimeException error) {
            // execute may already have persisted or broadcast. Retain the reservation even
            // on failure; only explicitly proven pre-business failures may be aborted.
            throw CommandGuard.failure(error);
        }
    }

    public <T> T create(String scope, String key, String fingerprint, Class<T> type,
                        Supplier<IdempotencyStore.Creation<T>> candidate,
                        Function<IdempotencyStore.Entry, T> legacyReplay) {
        var owner = idempotency.begin(scope, key, fingerprint);
        T cached = idempotency.replay(owner, type);
        if (cached != null) return cached;
        if (owner != null && owner.completed()) return legacyReplay.apply(owner);
        try {
            for (int attempt = 0; attempt < 32; attempt++) {
                var creation = candidate.get();
                if (idempotency.create(scope, key, owner, creation)) return creation.response();
            }
            throw new IllegalStateException("资源编号冲突，请查询原命令状态");
        } catch (RuntimeException failure) {
            // A Lua reply can be lost after all three writes committed. The same command's
            // completed receipt/resource association is the only valid recovery proof.
            try {
                T recovered = idempotency.replay(idempotency.receipt(scope, key), type);
                if (recovered != null) return recovered;
            } catch (RuntimeException ignored) { /* unknown remains unknown */ }
            throw new com.xiyouji.exception.ResultUnknownException();
        }
    }
}
