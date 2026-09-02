package com.xiyouji.service.session;

import com.xiyouji.exception.StateVersionConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存会话存储实现
 * 使用 ConcurrentHashMap 存储会话，默认启用（当 app.redis.session-enabled=false 或未设置时）
 */
@Component
@Primary
@ConditionalOnProperty(name = "app.redis.session-enabled", havingValue = "false", matchIfMissing = true)
public class InMemorySessionStore implements SessionStore {

    private static final Logger log = LoggerFactory.getLogger(InMemorySessionStore.class);

    private final ConcurrentHashMap<String, GameSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void put(String sessionId, GameSession session) {
        GameSession current = sessions.get(sessionId);
        // The service normally serializes writes with the session lock.  Keep
        // the store defensive as well so a stale object cannot overwrite a
        // newer aggregate when a caller bypasses that lock (or when a retry
        // races with a failover).
        if (current != null && current != session
                && current.getStateVersion() > session.getStateVersion()) {
            throw new StateVersionConflictException("session:" + sessionId,
                    session.getStateVersion(), current.getStateVersion());
        }
        session.setStateVersion(session.getStateVersion() + 1);
        sessions.put(sessionId, session);
        log.debug("会话已存入内存: sessionId={}", sessionId);
    }

    @Override
    public GameSession get(String sessionId) {
        return sessions.get(sessionId);
    }

    @Override
    public boolean remove(String sessionId) {
        boolean removed = sessions.remove(sessionId) != null;
        if (removed) {
            log.debug("会话已从内存移除: sessionId={}", sessionId);
        }
        return removed;
    }

    @Override
    public boolean exists(String sessionId) {
        return sessions.containsKey(sessionId);
    }
}
