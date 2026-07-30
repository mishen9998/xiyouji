package com.xiyouji.service.room;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 分布式锁服务
 * - 分布式部署：基于 Redisson 的 RLock 实现跨实例互斥
 * - 单机部署（standalone profile，无 Redis）：降级为 JVM 内 ReentrantLock
 * - 统一封装 tryLock + 自动释放，避免业务代码漏释放锁
 *
 * 使用方式：
 *   lockService.executeWithLock("room:join:ABCD1234", 5, () -> { ... });
 */
@Service
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);

    /** Redisson 客户端，单机模式下为 null */
    private final RedissonClient redissonClient;

    /** 单机降级用的本地锁映射（按 key 分组） */
    private final java.util.concurrent.ConcurrentHashMap<String, ReentrantLock> localLocks =
            new java.util.concurrent.ConcurrentHashMap<>();

    public DistributedLockService(ObjectProvider<RedissonClient> redissonClientProvider) {
        // 使用 ObjectProvider 可选注入：standalone profile 无 Redisson 自动配置时返回 null
        this.redissonClient = redissonClientProvider.getIfAvailable();
        log.info("DistributedLockService initialized: mode={}",
                redissonClient != null ? "REDISSON" : "LOCAL_FALLBACK");
    }

    /**
     * 在锁保护下执行任务，自动释放锁
     *
     * @param lockKey     锁键（建议带业务前缀，如 "room:join:ABCD1234"）
     * @param waitTimeSec 最多等待获取锁的时间（秒）
     * @param supplier    业务逻辑
     * @return 业务返回值
     * @throws RuntimeException 获取锁失败或业务执行异常时抛出
     */
    public <T> T executeWithLock(String lockKey, long waitTimeSec, Supplier<T> supplier) {
        if (redissonClient != null) {
            return executeWithRedissonLock(lockKey, waitTimeSec, supplier);
        }
        return executeWithLocalLock(lockKey, waitTimeSec, supplier);
    }

    /**
     * 在锁保护下执行无返回值任务
     */
    public void executeWithLock(String lockKey, long waitTimeSec, Runnable runnable) {
        executeWithLock(lockKey, waitTimeSec, () -> {
            runnable.run();
            return null;
        });
    }

    private <T> T executeWithRedissonLock(String lockKey, long waitTimeSec, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockKey);
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

    private <T> T executeWithLocalLock(String lockKey, long waitTimeSec, Supplier<T> supplier) {
        ReentrantLock lock = localLocks.computeIfAbsent(lockKey, k -> new ReentrantLock());
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
