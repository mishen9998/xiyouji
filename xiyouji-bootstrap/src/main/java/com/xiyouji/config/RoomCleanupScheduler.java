package com.xiyouji.config;

import com.xiyouji.service.room.RoomCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 房间清理定时任务
 * 默认每 5 分钟触发一次扫描（fixedDelay：上一次执行完成后开始计时）。
 * 双实例部署时两个实例都会运行；清理逻辑本身靠房间锁 + 双重确认 + 幂等删除保证安全，
 * 不需要 leader 选举。
 */
@Configuration
@ConditionalOnProperty(name = "app.room.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class RoomCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RoomCleanupScheduler.class);

    private final RoomCleanupService cleanupService;

    public RoomCleanupScheduler(RoomCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(fixedDelayString = "${app.room.cleanup.fixed-delay-ms:300000}")
    public void sweepIdleRooms() {
        int removed = cleanupService.sweep();
        if (removed > 0) {
            log.info("Room cleanup task dissolved {} idle room(s)", removed);
        }
    }
}