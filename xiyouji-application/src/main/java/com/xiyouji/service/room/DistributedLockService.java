package com.xiyouji.service.room;

import java.util.function.Supplier;

/** Application-facing lock port; Redis/JVM implementations live in infrastructure. */
public interface DistributedLockService {

    <T> T executeWithLock(String lockKey, long waitTimeSec, Supplier<T> supplier);

    default void executeWithLock(String lockKey, long waitTimeSec, Runnable runnable) {
        executeWithLock(lockKey, waitTimeSec, () -> {
            runnable.run();
            return null;
        });
    }
}
