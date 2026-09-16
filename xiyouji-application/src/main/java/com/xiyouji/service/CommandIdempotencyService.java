package com.xiyouji.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/** Spring adapter used by HTTP controllers to reserve and complete command keys. */
@Service
public class CommandIdempotencyService {

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;

    public CommandIdempotencyService(IdempotencyStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public IdempotencyStore.Entry begin(String scope, String key, String fingerprint) {
        if (key == null || key.isBlank()) return null;
        return CommandGuard.begin(store, scope + ":" + key, fingerprint);
    }

    public void complete(String scope, String key, IdempotencyStore.Entry owner, String value) {
        if (key != null && !key.isBlank()) {
            try {
                if (!store.complete(scope + ":" + key, owner, value, CommandGuard.TTL))
                    throw new com.xiyouji.exception.ResultUnknownException();
            } catch (RuntimeException failure) {
                throw new com.xiyouji.exception.ResultUnknownException();
            }
        }
    }

    /**
     * Complete a command with the exact JSON response that was returned to the
     * caller.  Replaying this value is important for commands whose response
     * contains a random reward or a newly generated resource identifier: a
     * retry must not reconstruct a different response from the current state.
     */
    public void completeResponse(String scope, String key, IdempotencyStore.Entry owner, Object response) {
        complete(scope, key, owner, serialize(response));
    }

    public String serialize(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法缓存幂等响应", e);
        }
    }

    /**
     * Decode a previously cached response.  Old pre-phase-two markers (for
     * example `done` or a room code) are intentionally treated as unavailable
     * so callers can use their backwards-compatible state refresh fallback.
     */
    public <T> T replay(IdempotencyStore.Entry entry, Class<T> responseType) {
        if (entry == null || !entry.completed() || entry.value() == null
                || entry.value().isBlank() || !entry.value().trim().startsWith("{")) {
            return null;
        }
        try {
            return objectMapper.readValue(entry.value(), responseType);
        } catch (Exception e) {
            throw new IllegalStateException("幂等响应已损坏，拒绝重复执行命令", e);
        }
    }

    public void abort(String scope, String key, IdempotencyStore.Entry owner) {
        if (key != null && !key.isBlank()) store.abort(scope + ":" + key, owner);
    }

    public boolean create(String scope, String key, IdempotencyStore.Entry owner,
                          IdempotencyStore.Creation<?> creation) {
        return store.create(scope + ":" + key, owner, creation, serialize(creation.response()));
    }

    public IdempotencyStore.Entry receipt(String scope, String key) {
        return store.find(scope + ":" + key).orElseThrow(com.xiyouji.exception.ResultUnknownException::new);
    }
}
