package com.xiyouji.service;

import com.xiyouji.exception.ResultUnknownException;
import com.xiyouji.service.room.Room;
import com.xiyouji.service.room.RoomStore;
import com.xiyouji.service.session.GameSession;
import com.xiyouji.service.session.SessionStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.*;

/** Single-process mode only; restart discards both resources and receipts. */
@Component
@ConditionalOnProperty(name = "app.redis.session-enabled", havingValue = "false", matchIfMissing = true)
public class LocalIdempotencyStore implements IdempotencyStore {
    private record Timed(Entry entry, long expiry) {}
    private final Map<String, Timed> keys = new HashMap<>();
    private final Map<String, Entry> resources = new HashMap<>();
    private RoomStore rooms;
    private SessionStore sessions;
    @Autowired
    public void configureResources(RoomStore rooms, SessionStore sessions) {
        this.rooms = rooms;
        this.sessions = sessions;
    }
    @Override public synchronized Optional<Entry> find(String key) {
        Entry linked = resources.get(key);
        if (linked != null) {
            String ref = linked.resourceRef();
            boolean exists = ref.startsWith("room:") ? rooms.exists(ref.substring(5)) : sessions.exists(ref.substring(8));
            if (exists) return Optional.of(linked);
            resources.remove(key);
        }
        Timed current = keys.get(key);
        if (current != null && current.expiry() <= System.currentTimeMillis()) {
            keys.remove(key);
            current = null;
        }
        return current == null ? Optional.empty() : Optional.of(current.entry());
    }
    @Override public synchronized boolean reserve(String key, Entry owner, Duration ttl) {
        if (find(key).isPresent()) return false;
        keys.put(key, new Timed(owner, System.currentTimeMillis() + ttl.toMillis()));
        return true;
    }
    private boolean owns(String key, Entry owner) {
        Entry current = find(key).orElse(null);
        return owner != null && owner.executionToken() != null && current != null && !current.completed()
                && owner.executionToken().equals(current.executionToken()) && owner.generation() == current.generation();
    }
    @Override public synchronized boolean complete(String key, Entry owner, String value, Duration ttl) {
        if (!owns(key, owner)) return false;
        keys.put(key, new Timed(owner.completed(value, null), System.currentTimeMillis() + ttl.toMillis()));
        return true;
    }
    @Override public synchronized boolean abort(String key, Entry owner) {
        if (!owns(key, owner)) return false;
        keys.remove(key);
        return true;
    }
    @Override public synchronized boolean renew(String key, Entry owner, Duration ttl) {
        if (!owns(key, owner)) return false;
        keys.put(key, new Timed(owner, System.currentTimeMillis() + ttl.toMillis()));
        return true;
    }
    @Override public synchronized boolean create(String key, Entry owner, Creation<?> creation, String response) {
        if (!owns(key, owner)) throw new ResultUnknownException();
        boolean created;
        if ("room".equals(creation.kind())) created = rooms.createIfAbsent((Room) creation.resource());
        else created = sessions.createIfAbsent((GameSession) creation.resource());
        if (!created) return false;
        Entry done = owner.completed(response, creation.kind() + ":" + creation.resourceRef());
        keys.put(key, new Timed(done, System.currentTimeMillis() + CommandGuard.TTL.toMillis()));
        resources.put(key, done);
        return true;
    }
    @Override public boolean tryAcquire(String key, Duration ttl) { return tryAcquire(key, "legacy", ttl); }
    @Override public boolean tryAcquire(String key, String fingerprint, Duration ttl) {
        return reserve(key, new Entry(fingerprint, "", false), ttl);
    }
}
