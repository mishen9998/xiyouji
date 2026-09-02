package com.xiyouji.service.room;

import com.xiyouji.exception.StorageUnavailableException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Redisson implementation of the application lock port. */
@Service
@ConditionalOnProperty(name = "app.redis.session-enabled", havingValue = "true")
public class RedisDistributedLockService implements DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLockService.class);
    private final RedissonClient redissonClient;

    public RedisDistributedLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
        log.info("DistributedLockService initialized: REDISSON");
    }

    @Override
    public <T> T executeWithLock(String lockKey, long waitTimeSec, Supplier<T> supplier) {
        RLock lock;
        try {
            lock = redissonClient.getLock(lockKey);
        } catch (Exception e) {
            throw unavailable(e);
        }
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
        } catch (RuntimeException e) {
            if (isRedisFailure(e)) {
                throw unavailable(e);
            }
            throw e;
        } finally {
            try {
                if (locked && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            } catch (Exception e) {
                log.warn("Unable to release Redis lock key={}: {}", lockKey, e.getMessage());
            }
        }
    }

    private StorageUnavailableException unavailable(Exception cause) {
        return new StorageUnavailableException("共享锁服务暂不可用，请稍后重试", cause);
    }

    private boolean isRedisFailure(RuntimeException e) {
        String name = e.getClass().getName().toLowerCase();
        String message = String.valueOf(e.getMessage()).toLowerCase();
        return name.contains("redis") || name.contains("redisson")
                || message.contains("redis") || message.contains("connection")
                || message.contains("timed out");
    }
}
