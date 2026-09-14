package com.xiyouji.service.room;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 房间清理服务
 * 定时扫描长时间无活动的僵尸房间（典型场景：玩家直接关闭浏览器，房间无人操作），
 * 在房间锁内做双重确认后自动解散，避免内存/Redis 中房间对象无限堆积。
 *
 * 设计要点：
 * - 活动时间由各 RoomStore 实现统一在 save() 时盖章，本服务只读。
 * - 清理前先取房间粒度的分布式锁，锁内重新读取并再次确认闲置状态，
 *   避免清理瞬间房间被并发写入恢复活跃而被误删。
 * - 双实例部署时两个实例都会运行扫描任务，靠锁 + 双重确认 + 幂等删除保证安全。
 *
 * 去框架化：无 @Service/@Value，配置由 bootstrap 层的 RoomCleanupConfig
 * 读取后经构造器注入（idleMinutes 为纯值）。
 */
public class RoomCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RoomCleanupService.class);

    private final RoomStore roomStore;
    private final MultiplayerBattleStore battleStore;
    private final DistributedLockService lockService;
    private final RoomEventPublisher eventPublisher;
    private final long idleMinutes;

    public RoomCleanupService(RoomStore roomStore,
                              MultiplayerBattleStore battleStore,
                              DistributedLockService lockService,
                              RoomEventPublisher eventPublisher,
                              long idleMinutes) {
        this.roomStore = roomStore;
        this.battleStore = battleStore;
        this.lockService = lockService;
        this.eventPublisher = eventPublisher;
        this.idleMinutes = idleMinutes;
    }

    /**
     * 扫描并清理闲置超时的房间，返回本次清理的数量。
     * 单房间清理失败不会中断整体扫描，也不会向上抛出异常，保证定时任务持续运行。
     */
    public int sweep() {
        try {
            List<Room> rooms = roomStore.findAll();
            int removed = 0;
            for (Room snapshot : rooms) {
                if (!isIdle(snapshot)) {
                    continue;
                }
                if (tryCleanup(snapshot.getCode())) {
                    removed++;
                }
            }
            if (removed > 0) {
                log.info("Room cleanup dissolved {} idle room(s)", removed);
            }
            return removed;
        } catch (RuntimeException e) {
            // 例如 Redis 暂不可用：本轮跳过，等待下一轮调度重试
            log.warn("Room cleanup sweep skipped: {}", e.getMessage());
            return 0;
        }
    }

    private boolean tryCleanup(String code) {
        try {
            return lockService.executeWithLock(RoomLockKeys.forRoom(code), 2, () -> {
                // 锁内重新读取做双重确认：防止清理瞬间房间被并发写入恢复活跃
                Room current = roomStore.get(code);
                if (current == null || !isIdle(current)) {
                    return false;
                }
                // 战斗中房间不清理：战斗状态由自己的 TTL 兜底，避免误杀耗时较长的正常战斗
                if (battleStore.exists(code)) {
                    return false;
                }
                roomStore.remove(code);
                battleStore.remove(code);
                eventPublisher.broadcastSystemMessage(code, "房间长时间无活动，已自动解散");
                log.info("Room cleanup dissolved idle room: code={}", code);
                return true;
            });
        } catch (RuntimeException e) {
            log.warn("Room cleanup failed for room {}: {}", code, e.getMessage());
            return false;
        }
    }

    private boolean isIdle(Room room) {
        LocalDateTime lastActiveAt = room.getLastActiveAt();
        if (lastActiveAt == null) {
            // 旧数据没有该字段：年龄未知，交给存储层 TTL 兜底，不主动清理
            return false;
        }
        return lastActiveAt.isBefore(LocalDateTime.now().minusMinutes(idleMinutes));
    }
}