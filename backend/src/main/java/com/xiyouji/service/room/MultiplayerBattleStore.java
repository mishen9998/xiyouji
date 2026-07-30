package com.xiyouji.service.room;

/**
 * 多人战斗状态存储接口
 * 抽象持久化方式，支持内存和Redis两种实现。
 */
public interface MultiplayerBattleStore {

    /** 保存战斗状态 */
    void save(MultiplayerBattleState state);

    /** 根据房间码获取战斗状态，不存在返回 null */
    MultiplayerBattleState get(String roomCode);

    /** 删除战斗状态 */
    boolean remove(String roomCode);

    /** 战斗状态是否存在 */
    boolean exists(String roomCode);
}
