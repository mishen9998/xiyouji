package com.xiyouji.service.room;

import com.xiyouji.exception.BusinessException;
import com.xiyouji.service.CommandGuard;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 房间访问模板
 *
 * 统一持有按房间粒度的分布式锁、取房间与状态版本校验，
 * 供成员管理 / 游戏进程 / 节点事件各组件共享，
 * 替代原先在 RoomService 中反复出现的"加锁-取房间-校验版本"三件套。
 */
@Component
public class RoomAccess {

    private static final String CREATE_LOCK_KEY = "xiyouji:lock:room:create";

    private final RoomStore roomStore;
    private final DistributedLockService lockService;

    public RoomAccess(RoomStore roomStore, DistributedLockService lockService) {
        this.roomStore = roomStore;
        this.lockService = lockService;
    }

    /** 获取房间领域对象，不存在则抛 404 */
    public Room getRoomOrThrow(String code) {
        Room room = roomStore.get(code);
        if (room == null) {
            throw new BusinessException("ROOM_NOT_FOUND", "房间不存在或已解散", 404);
        }
        return room;
    }

    public boolean roomExists(String code) {
        return roomStore.exists(code);
    }

    public boolean codeExists(String code) {
        return roomStore.codeExists(code);
    }

    void save(Room room) {
        roomStore.save(room);
    }

    void remove(String code) {
        roomStore.remove(code);
    }

    /** 期望版本号非法（-1 表示客户端未携带版本）时跳过校验 */
    public void checkVersion(String code, Room room, long expectedVersion) {
        if (expectedVersion >= 0) {
            CommandGuard.checkVersion("room:" + code, expectedVersion, room.getStateVersion());
        }
    }

    /** 按房间粒度加锁执行有返回值的动作 */
    public <T> T withRoomLock(String code, Supplier<T> action) {
        return lockService.executeWithLock(RoomLockKeys.forRoom(code), 5, action);
    }

    /** 按房间粒度加锁执行无返回值的动作 */
    public void withRoomLock(String code, Runnable action) {
        lockService.executeWithLock(RoomLockKeys.forRoom(code), 5, action);
    }

    /** 全局创建锁（保证房间码查重与写入原子） */
    public <T> T withCreateLock(Supplier<T> action) {
        return lockService.executeWithLock(CREATE_LOCK_KEY, 5, action);
    }
}