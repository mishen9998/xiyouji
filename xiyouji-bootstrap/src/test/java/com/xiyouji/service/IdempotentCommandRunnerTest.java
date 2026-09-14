package com.xiyouji.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * IdempotentCommandRunner 单元测试
 * 验证幂等命令模板的四条控制流：缓存命中、完成态回放、首次执行、失败中断。
 */
@DisplayName("幂等命令执行模板")
@ExtendWith(MockitoExtension.class)
class IdempotentCommandRunnerTest {

    @Mock private CommandIdempotencyService idempotency;

    private IdempotentCommandRunner runner;

    private static final String SCOPE = "game:battle:play:user:session1";

    @BeforeEach
    void setUp() {
        runner = new IdempotentCommandRunner(idempotency);
    }

    /** 规避 Class&lt;Map&gt; 与 Class&lt;Map&lt;String, Object&gt;&gt; 的泛型推断冲突 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> run(String key,
                                    Supplier<Map<String, Object>> execute,
                                    java.util.function.Function<IdempotencyStore.Entry, Map<String, Object>> replay) {
        return (Map<String, Object>) runner.run(SCOPE, key, "fp", (Class) Map.class,
                (Supplier) execute, (java.util.function.Function) replay);
    }

    @Test
    @DisplayName("缓存命中: 直接重放响应，不执行命令")
    void replay_returnsCachedResponse() {
        var previous = new IdempotencyStore.Entry("fp", "{\"inBattle\":true}", true);
        when(idempotency.begin(eq(SCOPE), eq("k1"), eq("fp"))).thenReturn(previous);
        Map<String, Object> cached = Map.of("inBattle", true);
        when(idempotency.replay(previous, Map.class)).thenReturn(cached);
        Supplier<Map<String, Object>> execute = mock(Supplier.class);

        Map<String, Object> result = run("k1", execute, p -> Map.of());

        assertSame(cached, result);
        verify(execute, never()).get();
        verify(idempotency, never()).completeResponse(any(), any(), any(), any());
    }

    @Test
    @DisplayName("完成态无缓存: 按状态重建响应")
    void completed_replaysFromCurrentState() {
        var previous = new IdempotencyStore.Entry("fp", " ", true);
        when(idempotency.begin(eq(SCOPE), eq("k1"), eq("fp"))).thenReturn(previous);
        when(idempotency.replay(previous, Map.class)).thenReturn(null);
        Map<String, Object> rebuilt = Map.of("stateVersion", 9);

        Map<String, Object> result = run("k1", () -> null, p -> rebuilt);

        assertSame(rebuilt, result);
        verify(idempotency, never()).abort(any(), any());
    }

    @Test
    @DisplayName("首次执行: 执行命令并缓存完整响应")
    void firstExecution_returnsAndCaches() {
        when(idempotency.begin(eq(SCOPE), eq("k1"), eq("fp"))).thenReturn(null);
        Map<String, Object> fresh = Map.of("stateVersion", 3);

        Map<String, Object> result = run("k1", () -> fresh, p -> Map.of());

        assertSame(fresh, result);
        verify(idempotency).completeResponse(SCOPE, "k1", "fp", fresh);
    }

    @Test
    @DisplayName("命令失败: 中断幂等标记并原样抛出")
    void failure_abortsAndRethrows() {
        when(idempotency.begin(eq(SCOPE), eq("k1"), eq("fp"))).thenReturn(null);
        IllegalArgumentException boom = new IllegalArgumentException("boom");

        assertThrows(IllegalArgumentException.class, () -> run("k1", () -> { throw boom; }, p -> Map.of()));

        verify(idempotency).abort(SCOPE, "k1");
    }
}