package com.xiyouji.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.xiyouji.service.room.Room;

/**
 * Room 持久化序列化 MixIn（infrastructure 层）
 *
 * 领域对象 Room 本身不携带任何序列化框架注解；Redis 存储需要排除的
 * 派生访问器（playerCount/full/mapOpen）在此集中声明，由 Redis 专用
 * ObjectMapper 注册，保证存储字节与既有数据兼容。
 */
public abstract class RoomPersistenceMixIn {

    @JsonIgnore
    public abstract int getPlayerCount();

    @JsonIgnore
    public abstract boolean isFull();

    @JsonIgnore
    public abstract boolean isMapOpen();
}