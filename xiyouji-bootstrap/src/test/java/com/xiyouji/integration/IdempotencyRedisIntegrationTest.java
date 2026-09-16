package com.xiyouji.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyouji.config.RedisConfig;
import com.xiyouji.exception.ResultUnknownException;
import com.xiyouji.service.*;
import com.xiyouji.service.room.*;
import com.xiyouji.service.session.*;
import com.xiyouji.model.GameCharacter;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class IdempotencyRedisIntegrationTest {
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2.5-alpine3.19").withExposedPorts(6379);
    static LettuceConnectionFactory factory;
    StringRedisTemplate redis;
    RedisTemplate<String, Object> resources;
    RedisSessionStore sessions;
    RedisRoomStore rooms;
    RedisIdempotencyStore store;
    final ObjectMapper mapper = new ObjectMapper();
    @BeforeAll static void connect() {
        factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet(); factory.start();
    }
    @AfterAll static void close() { factory.destroy(); }
    @BeforeEach void setup() {
        redis = new StringRedisTemplate(factory);
        // This Redis is exclusively owned by this test class on a random Docker port.
        try (var connection = factory.getConnection()) { connection.serverCommands().flushDb(); }
        resources = new RedisConfig().redisTemplate(factory);
        sessions = new RedisSessionStore(redis);
        rooms = new RedisRoomStore(resources);
        store = configured(new RedisIdempotencyStore(redis, mapper));
    }
    RedisIdempotencyStore configured(RedisIdempotencyStore candidate) {
        candidate.configureResources(resources, sessions); return candidate;
    }
    IdempotentCommandRunner runner(RedisIdempotencyStore candidate) {
        return new IdempotentCommandRunner(new CommandIdempotencyService(candidate, mapper));
    }
    IdempotencyStore.Creation<Map> room(String code) {
        Room room = new Room(code, "host"); room.setStateVersion(1);
        room.getPlayers().add(new RoomPlayer("host", "host"));
        return new IdempotencyStore.Creation<>("room", code, room, Map.of("code", code, "stateVersion", 1));
    }
    @Test void expiredOldOwnerCannotCompleteAbortOrRenewNewGeneration() throws Exception {
        var old = new IdempotencyStore.Entry("fp", "", false, "old", 1, null);
        assertTrue(store.reserve("cas", old, Duration.ofMillis(30)));
        Thread.sleep(80);
        var fresh = new IdempotencyStore.Entry("fp", "", false, "new", 2, null);
        assertTrue(store.reserve("cas", fresh, Duration.ofSeconds(30)));
        assertFalse(store.complete("cas", old, "old-result", CommandGuard.TTL));
        assertFalse(store.abort("cas", old));
        assertFalse(store.renew("cas", old, CommandGuard.TTL));
        assertTrue(store.renew("cas", fresh, CommandGuard.TTL));
        assertTrue(store.complete("cas", fresh, "new-result", CommandGuard.TTL));
        assertFalse(store.abort("cas", fresh));
        assertEquals("new-result", store.find("cas").orElseThrow().value());
    }
    @Test void legacyCompletedReplaysButLegacyInProgressCannotBeTakenOver() {
        redis.opsForValue().set(RedisIdempotencyStore.PREFIX + "done", "{\"fingerprint\":\"fp\",\"value\":\"old-response\",\"completed\":true}");
        assertEquals("old-response", CommandGuard.begin(store, "done", "fp").value());
        redis.opsForValue().set(RedisIdempotencyStore.PREFIX + "legacy", "{\"fingerprint\":\"fp\",\"value\":\"\",\"completed\":false}");
        var fake = new IdempotencyStore.Entry("fp", "", false, "attacker", 1, null);
        assertThrows(ResultUnknownException.class, () -> CommandGuard.begin(store, "legacy", "fp"));
        assertFalse(store.abort("legacy", fake)); assertFalse(store.complete("legacy", fake, "bad", CommandGuard.TTL));
        assertFalse(store.renew("legacy", fake, CommandGuard.TTL));
    }
    @Test void lostLuaReplyRecoversExactResourceAndCrossInstanceReplay() {
        var lossy = configured(new RedisIdempotencyStore(redis, mapper) {
            @Override public boolean create(String key, Entry owner, Creation<?> candidate, String response) {
                assertTrue(super.create(key, owner, candidate, response));
                throw new IllegalStateException("injected: Lua committed but response lost");
            }
        });
        AtomicInteger builds = new AtomicInteger();
        var result = runner(lossy).create("scope", "key", "fp", Map.class,
                () -> { builds.incrementAndGet(); return room("RECOVER1"); }, previous -> Map.of());
        assertEquals("RECOVER1", result.get("code"));
        assertNotNull(rooms.get("RECOVER1"));
        var secondReplica = configured(new RedisIdempotencyStore(redis, mapper));
        assertEquals(result, runner(secondReplica).create("scope", "key", "fp", Map.class,
                () -> { fail("must not build another resource"); return room("BAD00001"); }, previous -> Map.of()));
        assertEquals(1, builds.get());
        // Ten-minute command marker can expire while its resource and atomic association live.
        redis.delete(RedisIdempotencyStore.PREFIX + "scope:key");
        assertEquals("room:RECOVER1", secondReplica.find("scope:key").orElseThrow().resourceRef());
        redis.expire(RedisIdempotencyStore.LINK_PREFIX + "scope:key", Duration.ofSeconds(1));
        rooms.save(rooms.get("RECOVER1"));
        assertTrue(redis.getExpire(RedisIdempotencyStore.LINK_PREFIX + "scope:key") > 7100);
    }
    @Test void collisionRetriesSameOwnerAndNeverOverwritesResource() {
        Room existing = (Room) room("COLLIDE1").resource(); existing.setHostUserId("original"); rooms.save(existing);
        AtomicInteger attempts = new AtomicInteger();
        var result = runner(store).create("scope", "key", "fp", Map.class,
                () -> room(attempts.incrementAndGet() == 1 ? "COLLIDE1" : "UNIQUE01"), entry -> Map.of());
        assertEquals(2, attempts.get()); assertEquals("UNIQUE01", result.get("code"));
        assertEquals("original", rooms.get("COLLIDE1").getHostUserId());
        assertEquals("room:UNIQUE01", store.find("scope:key").orElseThrow().resourceRef());
    }
    @Test void concurrentCandidatesHaveExactlyOneResourceWinner() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> outcomes = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                String key = "command" + i;
                outcomes.add(pool.submit(() -> {
                    var owner = CommandGuard.begin(store, key, "fp");
                    return store.create(key, owner, room("CONCUR01"), "{\"code\":\"CONCUR01\"}");
                }));
            }
            int wins = 0; for (var outcome : outcomes) if (outcome.get()) wins++;
            assertEquals(1, wins); assertNotNull(rooms.get("CONCUR01"));
        } finally { pool.shutdownNow(); }
    }
    @Test void failureAfterReserveBeforeLuaNeverAutomaticallyReexecutes() {
        var broken = configured(new RedisIdempotencyStore(redis, mapper) {
            @Override public boolean create(String key, Entry owner, Creation<?> candidate, String response) {
                throw new IllegalStateException("injected before Lua");
            }
        });
        AtomicInteger builds = new AtomicInteger();
        for (int i = 0; i < 2; i++) assertThrows(ResultUnknownException.class,
                () -> runner(broken).create("scope", "key", "fp", Map.class,
                        () -> { builds.incrementAndGet(); return room("NOTMADE1"); }, entry -> Map.of()));
        assertEquals(1, builds.get()); assertNull(rooms.get("NOTMADE1"));
        assertFalse(store.find("scope:key").orElseThrow().completed());
    }
    @Test void completeFailureAfterBusinessRetainsMarkerAndDoesNotWriteAgain() {
        var broken = configured(new RedisIdempotencyStore(redis, mapper) {
            @Override public boolean complete(String key, Entry owner, String response, Duration ttl) {
                throw new IllegalStateException("injected unavailable complete");
            }
        });
        AtomicInteger writes = new AtomicInteger();
        for (int i = 0; i < 2; i++) assertThrows(ResultUnknownException.class, () -> runner(broken).run("scope", "key", "fp", Map.class,
                () -> { writes.incrementAndGet(); rooms.save((Room) room("WRITTEN1").resource()); return Map.of("ok", true); }, entry -> Map.of()));
        assertEquals(1, writes.get()); assertNotNull(rooms.get("WRITTEN1"));
        assertFalse(store.find("scope:key").orElseThrow().completed());
    }
    @Test void newGamePersistsSessionAndReceiptTogether() {
        GameSession session = new GameSession("session1", new GameCharacter(), new ArrayList<>());
        session.setOwnerUserId("host"); session.setStateVersion(1);
        var creation = new IdempotencyStore.Creation<Map>("session", "session1", session, Map.of("sessionId", "session1"));
        runner(store).create("game:new:host", "key", "fp", Map.class, () -> creation, entry -> Map.of());
        assertEquals("host", sessions.get("session1").getOwnerUserId());
        assertEquals("session:session1", store.find("game:new:host:key").orElseThrow().resourceRef());
        assertTrue(redis.getExpire(RedisIdempotencyStore.LINK_PREFIX + "game:new:host:key") > 86000);
    }
}
