package com.xiyouji.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the external services used by distributed mode against real Docker
 * containers. The class is disabled automatically on machines without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class DistributedInfrastructureContainersTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.39")
            .withDatabaseName("xiyouji")
            .withUsername("root")
            .withPassword("integration-test-password")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2.5-alpine3.19")
            .withExposedPorts(6379)
            .withCommand("redis-server", "--appendonly", "yes");

    private static RedissonClient redisson;

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        redisson = Redisson.create(config);
    }

    @AfterAll
    static void closeClients() {
        if (redisson != null) {
            redisson.shutdown();
        }
    }

    @Test
    void flywayCreatesAndUpgradesTheProductionSchema() throws SQLException {
        try (Connection connection = MYSQL.createConnection("")) {
            assertThat(columnExists(connection, "cards", "draw_next_turn")).isTrue();
            assertThat(columnExists(connection, "cards", "energy_next_turn")).isTrue();
            assertThat(columnExists(connection, "cards", "upgraded")).isTrue();
            assertThat(columnExists(connection, "users", "account")).isTrue();

            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("version")).isEqualTo("1");
                assertThat(result.getBoolean("success")).isTrue();
                assertThat(result.next()).isTrue();
                assertThat(result.getString("version")).isEqualTo("2");
                assertThat(result.getBoolean("success")).isTrue();
                assertThat(result.next()).isTrue();
                assertThat(result.getString("version")).isEqualTo("3");
                assertThat(result.getBoolean("success")).isTrue();
                assertThat(result.next()).isTrue();
                assertThat(result.getString("version")).isEqualTo("4");
                assertThat(result.getBoolean("success")).isTrue();
            }
        }
    }

    @Test
    void redisProvidesSharedStateAndPubSub() throws InterruptedException {
        String key = "integration:room:TEST";
        redisson.getBucket(key).set("state-v12", Duration.ofMinutes(1));
        assertThat((String) redisson.getBucket(key).get()).isEqualTo("state-v12");

        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> message = new AtomicReference<>();
        RTopic topic = redisson.getTopic("xiyouji:room-events");
        int listenerId = topic.addListener(String.class, (channel, value) -> {
            message.set(value);
            received.countDown();
        });
        try {
            topic.publish("ROOM_UPDATED:v12");
            assertTrue(received.await(5, TimeUnit.SECONDS), "Redis Pub/Sub message was not delivered");
            assertThat(message).hasValue("ROOM_UPDATED:v12");
        } finally {
            topic.removeListener(listenerId);
            redisson.getBucket(key).delete();
        }
    }

    @Test
    void redissonLockAllowsOnlyOneOwner() throws InterruptedException {
        RLock lock = redisson.getLock("xiyouji:lock:room:TEST");
        assertTrue(lock.tryLock(1, 10, TimeUnit.SECONDS));
        try {
            CountDownLatch attempted = new CountDownLatch(1);
            AtomicReference<Boolean> secondAcquired = new AtomicReference<>();
            Thread contender = new Thread(() -> {
                try {
                    secondAcquired.set(lock.tryLock(100, 500, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    secondAcquired.set(false);
                } finally {
                    attempted.countDown();
                }
            });
            contender.start();
            assertTrue(attempted.await(3, TimeUnit.SECONDS));
            contender.join(3000);
            assertFalse(Boolean.TRUE.equals(secondAcquired.get()), "a second owner acquired the room lock");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
}
