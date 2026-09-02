package com.xiyouji.service;

import com.xiyouji.exception.IdempotencyInProgressException;
import com.xiyouji.exception.IdempotencyKeyReusedException;
import com.xiyouji.exception.StateVersionConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommandGuardTest {

    @Test
    void concurrentSameKeyOnlyOneRequestAcquires() throws Exception {
        LocalIdempotencyStore store = new LocalIdempotencyStore();
        String fingerprint = CommandGuard.fingerprint("POST", "/test", "{}");
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(executor.submit(() -> {
                start.await();
                try {
                    CommandGuard.begin(store, "same-key", fingerprint);
                    return true;
                } catch (IdempotencyInProgressException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        long acquired = 0;
        for (Future<Boolean> future : futures) if (future.get()) acquired++;
        executor.shutdownNow();
        assertEquals(1, acquired);
    }

    @Test
    void sameKeyDifferentFingerprintIsRejectedAndFailureCanRetry() {
        LocalIdempotencyStore store = new LocalIdempotencyStore();
        String key = "reuse-key";
        String first = CommandGuard.fingerprint("POST", "/test", "one");
        String second = CommandGuard.fingerprint("POST", "/test", "two");
        CommandGuard.begin(store, key, first);
        assertThrows(IdempotencyKeyReusedException.class, () -> CommandGuard.begin(store, key, second));
        store.remove(key);
        assertFalse(CommandGuard.begin(store, key, second).completed());
    }

    @Test
    void staleVersionContainsCurrentVersion() {
        StateVersionConflictException error = assertThrows(StateVersionConflictException.class,
                () -> CommandGuard.checkVersion("session:s1", 2, 3));
        assertEquals(3, error.getCurrentStateVersion());
        assertEquals("STATE_VERSION_CONFLICT", error.getErrorCode());
    }

    @Test
    void completedCommandReplaysTheOriginalResponse() {
        LocalIdempotencyStore store = new LocalIdempotencyStore();
        CommandIdempotencyService service = new CommandIdempotencyService(store, new ObjectMapper());
        String fingerprint = CommandGuard.fingerprint("POST", "/test", "{}");
        service.begin("scope", "key", fingerprint);

        Map<String, Object> original = Map.of("stateVersion", 7, "reward", "宝箱遗物");
        service.completeResponse("scope", "key", fingerprint, original);

        IdempotencyStore.Entry entry = store.find("scope:key").orElseThrow();
        assertEquals(original, service.replay(entry, Map.class));
    }
}
