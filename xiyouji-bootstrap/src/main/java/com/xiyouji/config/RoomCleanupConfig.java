package com.xiyouji.config;

import com.xiyouji.service.room.DistributedLockService;
import com.xiyouji.service.room.MultiplayerBattleStore;
import com.xiyouji.service.room.RoomCleanupService;
import com.xiyouji.service.room.RoomEventPublisher;
import com.xiyouji.service.room.RoomStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 房间清理装配（bootstrap 层）
 *
 * 将 yml 配置（app.room.cleanup.idle-minutes）收敛在此处，
 * 以纯值构造注入应用层的 RoomCleanupService。
 */
@Configuration
public class RoomCleanupConfig {

    @Bean
    public RoomCleanupService roomCleanupService(RoomStore roomStore,
                                                 MultiplayerBattleStore battleStore,
                                                 DistributedLockService lockService,
                                                 RoomEventPublisher eventPublisher,
                                                 @Value("${app.room.cleanup.idle-minutes:30}") long idleMinutes) {
        return new RoomCleanupService(roomStore, battleStore, lockService, eventPublisher, idleMinutes);
    }
}