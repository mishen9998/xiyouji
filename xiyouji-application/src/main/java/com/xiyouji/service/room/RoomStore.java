package com.xiyouji.service.room;

/**
 * 房间存储接口
 * 抽象房间持久化方式，支持内存和Redis两种实现。
 * 类似 SessionStore 的设计，便于在不同部署环境下切换。
 */
public interface RoomStore {

    /** 保存房间（覆盖写） */
    void save(Room room);

    /** 根据房间码获取房间，不存在返回 null */
    Room get(String code);

    /** 删除房间，返回是否删除成功 */
    boolean remove(String code);

    /** 房间是否存在 */
    boolean exists(String code);

    /** 判断某房间码是否已被占用（用于生成唯一房间码） */
    boolean codeExists(String code);
}
