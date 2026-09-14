package com.xiyouji.integration;

import com.xiyouji.model.Card;
import com.xiyouji.model.enums.CardType;
import com.xiyouji.model.enums.Rarity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hibernate 映射权威验证：启动完整 Spring 上下文（含 MySQL/Redis 容器），
 * 确认 orm.xml 声明的五个领域实体全部注册为 JPA 托管类型并可读写。
 *
 * 背景：领域模型已去 JPA 注解（映射收敛于 META-INF/orm.xml），若注册路径
 * 失效（如 fat jar 环境自动发现不可靠），仓库初始化会抛
 * "Not a managed type"——本测试在真实上下文（而非仅编译）层面锁定契约。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("distributed")
class HibernateMappingIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.39")
            .withDatabaseName("xiyouji")
            .withUsername("root")
            .withPassword("test-pass");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2.5-alpine3.19")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("DB_PASSWORD", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("JWT_SECRET",
                () -> "integration-test-jwt-secret-must-be-at-least-32-bytes-long");
    }

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("orm.xml 声明的五个实体全部注册为 JPA 托管类型")
    void allDomainEntitiesAreManaged() {
        Set<String> managed = entityManager.getMetamodel().getEntities().stream()
                .map(EntityType::getName)
                .collect(Collectors.toSet());

        assertThat(managed)
                .contains(
                        "User",
                        "Card",
                        "Enemy",
                        "Relic",
                        "GameCharacter");
    }

    @Test
    @Transactional
    @DisplayName("Card 映射列可完成持久化往返")
    void cardPersistAndReloadRoundtrip() {
        Card card = new Card();
        card.setName("集成测试卡牌");
        card.setType(CardType.ATTACK);
        card.setRarity(Rarity.COMMON);
        card.setDescription("由 HibernateMappingIntegrationTest 写入");
        card.setCost(1);
        card.setDamage(6);

        entityManager.persist(card);
        entityManager.flush();
        entityManager.clear();

        Card loaded = entityManager.createQuery(
                        "select c from Card c where c.name = :name", Card.class)
                .setParameter("name", "集成测试卡牌")
                .getSingleResult();

        assertThat(loaded.getDamage()).isEqualTo(6);
        assertThat(loaded.getDescription()).isEqualTo("由 HibernateMappingIntegrationTest 写入");
        assertThat(loaded.getType()).isEqualTo(CardType.ATTACK);
    }
}