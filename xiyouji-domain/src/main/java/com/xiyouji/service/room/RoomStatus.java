package com.xiyouji.service.room;

/**
 * 房间状态枚举
 */
public enum RoomStatus {
    /** 等待玩家加入/准备 */
    WAITING,
    /** 地图探索中（移动、事件） */
    IN_MAP,
    /** 战斗中 */
    IN_BATTLE,
    /** 已结束（通关/全员阵亡/房主解散） */
    FINISHED
}
