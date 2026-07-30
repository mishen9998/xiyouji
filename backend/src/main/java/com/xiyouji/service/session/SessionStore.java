package com.xiyouji.service.session;

/**
 * 会话存储抽象接口
 * 定义游戏会话的存储操作，支持内存和Redis两种实现
 */
public interface SessionStore {

    /**
     * 存储或更新会话
     *
     * @param sessionId 会话ID
     * @param session   游戏会话对象
     */
    void put(String sessionId, GameSession session);

    /**
     * 获取会话
     *
     * @param sessionId 会话ID
     * @return 游戏会话对象，不存在则返回null
     */
    GameSession get(String sessionId);

    /**
     * 移除会话
     *
     * @param sessionId 会话ID
     * @return 如果会话存在并被移除则返回true，否则返回false
     */
    boolean remove(String sessionId);

    /**
     * 检查会话是否存在
     *
     * @param sessionId 会话ID
     * @return 存在返回true，否则返回false
     */
    boolean exists(String sessionId);
}
