package com.xiyouji.service.room;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** JVM lock implementation used only by the standalone profile. */
@Service
@ConditionalOnProperty(name = "app.redis.session-enabled", havingValue = "false", matchIfMissing = true)
public class LocalDistributedLockService implements DistributedLockService {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public <T> T executeWithLock(String lockKey, long waitTimeSec, Supplier<T> supplier) {
        ReentrantLock lock = locks.computeIfAbsent(lockKey, ignored -> new ReentrantLock());
        boolean locked = false;
        try {
            locked = lock.tryLock(waitTimeSec, TimeUnit.SECONDS);
            if (!locked) {
                throw new IllegalStateException("操作过于频繁，请稍后重试（获取锁超时 key=" + lockKey + "）");
            }
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("获取锁被中断 key=" + lockKey, e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
