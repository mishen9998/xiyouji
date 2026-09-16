package com.xiyouji.integration;

import com.xiyouji.model.Card;
import com.xiyouji.model.Enemy;
import com.xiyouji.combat.*;
import com.xiyouji.config.DataInitializer;
import com.xiyouji.port.EnemyRepositoryPort;
import com.xiyouji.service.battle.EnemyBehaviorGuard;
import com.xiyouji.exception.BusinessException;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Autowired private EnemyRepositoryPort enemies;
    @Autowired private DataInitializer initializer;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @Transactional
    void all63EnemyDefinitionsReloadFromJpaAndCycleWithoutFallback() {
        entityManager.clear();
        var loaded = enemies.findAll();
        assertThat(loaded).hasSize(63);
        assertThat(loaded.stream().filter(e -> !e.isBoss()).count()).isEqualTo(35);
        assertThat(loaded.stream().filter(Enemy::isBoss).count()).isEqualTo(28);
        for (Enemy enemy : loaded) {
            assertThat(enemy.getActionDefinitions()).isEqualTo(EnemyContentCatalog.require(enemy.getName()).actions());
            assertThat(enemy.getMovePattern()).isNull();
            assertThat(enemy.getRulesVersion()).isEqualTo(CombatRules.VERSION);
            assertThat(enemy.getContentVersion()).isEqualTo(2);
            EnemyCombat.validate(enemy);
            for (int i = 0; i < enemy.getActionDefinitions().size() * 2; i++) {
                var action = EnemyCombat.lockNextAction(enemy, List.of("p1", "p2"), 42);
                assertThat(action.actionType()).isEqualTo(enemy.getActionDefinitions()
                        .get(i % enemy.getActionDefinitions().size()).actionType());
            }
        }
    }

    @Test
    @Transactional
    void seedUpgradeIsIdempotentAndPreservesEveryEnemyIdStatAndArtwork() {
        String fields = "id,name,description,max_hp,hp,attack,defense,is_boss,level,emoji";
        var original = jdbc.queryForList("select " + fields + " from enemies order by id");
        jdbc.update("update enemies set content_key=null,content_version=0,rules_version=null,action_definitions=null");
        entityManager.clear();
        initializer.init();
        entityManager.flush();
        entityManager.clear();
        assertThat(jdbc.queryForList("select " + fields + " from enemies order by id")).isEqualTo(original);
        var firstUpgrade = jdbc.queryForList("select id,content_key,content_version,rules_version,action_definitions from enemies order by id");
        assertThat(enemies.findAll()).allMatch(e -> e.getActionDefinitions() != null && e.getContentVersion() == 2);
        initializer.init();
        entityManager.flush();
        entityManager.clear();
        assertThat(jdbc.queryForList("select id,content_key,content_version,rules_version,action_definitions from enemies order by id")).isEqualTo(firstUpgrade);
    }

    @Test
    @Transactional
    void unknownPersistedActionPreventsBattleWithDiagnosticError() {
        Long id = enemies.findByName("寅将军").get(0).getId();
        jdbc.update("update enemies set action_definitions=? where id=?",
                "[{\"actionType\":\"UNKNOWN_SUMMON\",\"targetScope\":\"SINGLE\",\"damagePercent\":100,\"hits\":1,\"strengthGain\":0,\"statusEffects\":{}}]", id);
        entityManager.clear();
        assertThatThrownBy(() -> EnemyBehaviorGuard.read(() -> enemies.findById(id)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UNKNOWN_SUMMON")
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo("ENEMY_BEHAVIOR_INVALID"));
    }

    @Test
    void upgradeFromRealV4SchemaPreservesExistingRowsAndChecksums() {
        jdbc.execute("CREATE DATABASE enemy_migration_upgrade CHARACTER SET utf8mb4");
        String url = MYSQL.getJdbcUrl().replace("/xiyouji", "/enemy_migration_upgrade");
        var source = new DriverManagerDataSource(url, MYSQL.getUsername(), MYSQL.getPassword());
        Flyway old = Flyway.configure().dataSource(source).target("4").load();
        old.migrate();
        var checksums = Arrays.stream(old.info().applied()).map(info -> info.getChecksum()).toList();
        JdbcTemplate upgradeJdbc = new JdbcTemplate(source);
        upgradeJdbc.update("INSERT INTO enemies(id,name,max_hp,hp,attack,defense,is_boss,level,emoji) VALUES(987,'寅将军',28,28,5,0,false,1,'🐯')");
        var before = upgradeJdbc.queryForMap("select id,name,max_hp,hp,attack,defense,is_boss,level,emoji from enemies where id=987");
        Flyway current = Flyway.configure().dataSource(source).load();
        assertThat(current.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(current.migrate().migrationsExecuted).isZero();
        assertThat(Arrays.stream(current.info().applied()).limit(4).map(info -> info.getChecksum()).toList()).isEqualTo(checksums);
        assertThat(upgradeJdbc.queryForMap("select id,name,max_hp,hp,attack,defense,is_boss,level,emoji from enemies where id=987")).isEqualTo(before);
        assertThat(upgradeJdbc.queryForObject("select content_version from enemies where id=987", Integer.class)).isZero();
        assertThat(current.validateWithResult().validationSuccessful).isTrue();
    }

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
