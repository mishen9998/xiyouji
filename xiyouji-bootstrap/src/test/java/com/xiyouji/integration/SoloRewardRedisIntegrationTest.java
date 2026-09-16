package com.xiyouji.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyouji.combat.EnemyContentCatalog;
import com.xiyouji.config.RedisConfig;
import com.xiyouji.controller.BattleController;
import com.xiyouji.controller.support.CurrentUserResolver;
import com.xiyouji.dto.PlayerSummaryAssembler;
import com.xiyouji.exception.GlobalExceptionHandler;
import com.xiyouji.model.*;
import com.xiyouji.model.enums.*;
import com.xiyouji.port.*;
import com.xiyouji.service.*;
import com.xiyouji.service.battle.*;
import com.xiyouji.service.room.LocalDistributedLockService;
import com.xiyouji.service.session.*;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Redis copies/CAS and command receipts. Only catalog repositories are controlled fixtures. */
@Testcontainers
class SoloRewardRedisIntegrationTest {
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2.5-alpine3.19")
            .withExposedPorts(6379);
    private static LettuceConnectionFactory factory;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private RedisSessionStore sessions;
    private RedisIdempotencyStore commands;
    private GameService game;
    private BattleService battles;
    private MockMvc mvc;
    private List<Enemy> enemies;
    private CardRepositoryPort cards;
    private String id;
    private static final String OWNER = "solo-owner";

    @BeforeAll static void connect() {
        factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        factory.start();
    }

    @AfterAll static void disconnect() { factory.destroy(); }

    @BeforeEach void setup() {
        id = "reward-" + UUID.randomUUID();
        StringRedisTemplate redis = new StringRedisTemplate(factory);
        sessions = new RedisSessionStore(redis);
        commands = new RedisIdempotencyStore(redis, json);
        commands.configureResources(new RedisConfig().redisTemplate(factory), sessions);
        cards = mock(CardRepositoryPort.class);
        var enemyRepo = mock(EnemyRepositoryPort.class);
        var relicRepo = mock(RelicRepositoryPort.class);
        when(relicRepo.findAll()).thenAnswer(inv -> new ArrayList<Relic>());
        Card reward = new Card("回归奖励", "", CardType.ATTACK, Rarity.COMMON, CharacterClass.SUN_WUKONG, 1);
        reward.setId(700L);
        reward.setDamage(6);
        when(cards.findByCharacterClassOrCharacterClassIsNull(CharacterClass.SUN_WUKONG)).thenReturn(List.of(reward));
        enemies = new ArrayList<>();
        for (EnemyContentCatalog.Entry entry : EnemyContentCatalog.entries()) {
            Enemy enemy = new Enemy(entry.name(), 100, 10, 5, entry.boss(), entry.level());
            enemy.setId((long) enemies.size() + 1);
            EnemyContentCatalog.upgrade(enemy);
            enemies.add(enemy);
        }
        when(enemyRepo.findAll()).thenReturn(enemies);
        when(enemyRepo.findById(anyLong())).thenAnswer(inv -> enemies.stream()
                .filter(e -> e.getId().equals(inv.getArgument(0))).findFirst());
        game = new GameService(mock(CharacterRepositoryPort.class), cards, relicRepo,
                new MapService(enemyRepo), mock(ShopService.class), sessions, new LocalDistributedLockService());
        SoloRelicTriggers relics = new SoloRelicTriggers();
        battles = new BattleService(game, new SoloBattleStarter(game, enemyRepo, relics),
                new SoloCardPlayHandler(game), new SoloTurnCoordinator(game, relics),
                new SoloRewardService(game), new SoloBattleInfoAssembler());
        mvc = controller(commands);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(OWNER, null, List.of()));
    }

    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }

    private MockMvc controller(RedisIdempotencyStore commandStore) {
        var runner = new IdempotentCommandRunner(new CommandIdempotencyService(commandStore, json));
        return MockMvcBuilders.standaloneSetup(new BattleController(battles, game,
                        new PlayerSummaryAssembler(), runner, new CurrentUserResolver()))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private Enemy enemy(String name) {
        return enemies.stream().filter(e -> e.getName().equals(name)).findFirst().orElseThrow();
    }

    private GameSession seed(boolean boss) {
        GameCharacter player = new GameCharacter();
        player.setCharacterClass(CharacterClass.SUN_WUKONG);
        player.setMaxHp(100);
        player.setHp(100);
        player.setGold(100);
        Card finisher = new Card("胜利回归牌", "", CardType.ATTACK, Rarity.BASIC, null, 0);
        finisher.setDamage(10000);
        for (int i = 0; i < 10; i++) player.getDeck().add(finisher.copy());
        Relic onKill = new Relic("测试炼妖壶", "", RelicTier.COMMON, "");
        onKill.setEffect("ON_KILL;MAX_HP:2");
        player.getRelics().add(onKill);
        MapNode first = new MapNode("first", 1, 5, 0, boss ? "BOSS" : "BATTLE", "首战");
        first.setEnemyId(String.valueOf(enemy(boss ? "黑熊精" : "寅将军").getId()));
        first.setVisited(true);
        MapNode next = new MapNode("second", 1, 6, 0, "BATTLE", "后续战斗");
        next.setEnemyId(String.valueOf(enemy("熊山君").getId()));
        next.setAccessible(true);
        GameSession session = new GameSession(id, player, new ArrayList<>(List.of(first, next)));
        session.setOwnerUserId(OWNER);
        session.setCurrentNode(first);
        session.setMapOpen(false);
        session.setNextBattleBlock(8);
        sessions.put(id, session);
        assertNotSame(session, sessions.get(id), "The fixture must cross a real serialization boundary");
        return sessions.get(id);
    }

    private JsonNode command(String action, String key, String body, long version) throws Exception {
        return json.readTree(mvc.perform(post("/api/game/" + action + "/" + id)
                        .header("X-Expected-State-Version", version).header("X-Idempotency-Key", key)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private JsonNode state() throws Exception {
        return json.readTree(mvc.perform(get("/api/game/battle/state/" + id))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test void victoryResponseAndReplayMatchCommittedRewardsAndCanContinueToNextBattle() throws Exception {
        GameSession seed = seed(false);
        JsonNode start = command("battle/start", "start", "{}", seed.getStateVersion());
        assertEquals(8, start.at("/player/block").asInt(), "Keep T3's one-shot opening block");
        assertEquals("ATTACK", start.at("/enemy/intent").asText());
        assertFalse(start.path("storyEvent").isMissingNode());
        JsonNode turn = command("battle/endturn", "turn", "{}", start.path("stateVersion").asLong());
        assertEquals("DEFEND", turn.at("/enemy/intent").asText(), "Keep T2's 寅将军 cycle");
        long version = turn.path("stateVersion").asLong();
        GameSession oldSnapshot = sessions.get(id);
        JsonNode won = command("battle/play", "kill", "{\"handIndex\":0}", version);
        GameSession saved = sessions.get(id);
        assertTrue(won.path("victory").asBoolean());
        assertEquals(5, won.at("/rewards/cardRewards").size());
        assertEquals(30, won.at("/rewards/goldReward").asInt());
        assertEquals(130, won.at("/player/gold").asInt());
        assertEquals(oldSnapshot.getPlayer().getMaxHp() + 2, won.at("/player/maxHp").asInt(),
                "ON_KILL belongs to the same saved aggregate");
        assertEquals(saved.getStateVersion(), won.path("stateVersion").asLong());
        assertEquals(version + 2, saved.getStateVersion(), "One action save plus one reward/map save; no stale outer save");
        assertTrue(saved.isMapOpen());
        assertFalse(oldSnapshot.getBattle().isRewardsHandled(), "Earlier reads must remain detached");
        assertEquals(won, state(), "GET and successful command expose the same reward and player snapshot");
        assertEquals(won, command("battle/play", "kill", "{\"handIndex\":0}", version));
        assertEquals(saved.getStateVersion(), sessions.get(id).getStateVersion(), "Same key must only replay");
        assertTrue(commands.find("game:battle:play:" + OWNER + ":" + id + ":kill").orElseThrow().completed());
        assertEquals(1, saved.getBattle().getCardsPlayedThisTurn());
        verify(cards, times(1)).findByCharacterClassOrCharacterClassIsNull(CharacterClass.SUN_WUKONG);

        JsonNode picked = command("reward/choose", "choose", "{\"cardIndex\":0}", saved.getStateVersion());
        assertEquals(11, sessions.get(id).getPlayer().getDeck().size());
        assertEquals(picked, command("reward/choose", "choose", "{\"cardIndex\":0}", saved.getStateVersion()));
        assertEquals(11, sessions.get(id).getPlayer().getDeck().size());
        assertTrue(state().at("/rewards/resolved").asBoolean());
        game.moveToNode(id, "second", picked.path("stateVersion").asLong(), OWNER);
        JsonNode second = command("battle/start", "second-start", "{}", sessions.get(id).getStateVersion());
        assertEquals("熊山君", second.at("/enemy/name").asText());
        assertEquals(0, second.at("/player/block").asInt(), "Opening event block is consumed exactly once");
        assertEquals(130, second.at("/player/gold").asInt());
        assertFalse(second.path("battleOver").asBoolean());
    }

    @Test void poisonVictoryAndDefeatBothReturnThePersistedTerminalSnapshot() throws Exception {
        for (boolean victory : List.of(true, false)) {
            id = "terminal-" + UUID.randomUUID();
            GameSession initial = seed(false);
            command("battle/start", "start", "{}", initial.getStateVersion());
            GameSession ready = sessions.get(id);
            if (victory) {
                ready.getBattle().getEnemy().setHp(1);
                ready.getBattle().getEnemy().addBuff(BuffType.POISON, 1);
            } else {
                ready.getPlayer().setHp(1);
                ready.getPlayer().setBlock(999);
                ready.getPlayer().getBuffs().put(BuffType.POISON, 2);
            }
            sessions.put(id, ready);
            JsonNode ended = command("battle/endturn", "end", "{}", ready.getStateVersion());
            assertTrue(ended.path("battleOver").asBoolean());
            assertEquals(victory, ended.path("victory").asBoolean());
            assertEquals(victory ? 5 : 0, ended.at("/rewards/cardRewards").size());
            assertEquals(victory ? 130 : 100, ended.at("/player/gold").asInt());
            assertEquals(ended, state());
            assertEquals(sessions.get(id).getStateVersion(), ended.path("stateVersion").asLong());
            assertTrue(sessions.get(id).isMapOpen());
        }
    }

    @Test void bossRewardsCanBeSkippedAndAuthoritativeProgressionCompletesThreeLayers() throws Exception {
        seed(true);
        for (int layer = 1; layer <= 3; layer++) {
            GameSession session = sessions.get(id);
            if (layer > 1) {
                session.setCurrentNode(session.getMap().stream().filter(n -> "BOSS".equals(n.getType())).findFirst().orElseThrow());
                sessions.put(id, session);
            }
            JsonNode start = command("battle/start", "start-" + layer, "{}", session.getStateVersion());
            JsonNode won = command("battle/play", "kill-" + layer, "{\"handIndex\":0}", start.path("stateVersion").asLong());
            assertTrue(won.path("victory").asBoolean());
            assertEquals(75 + 5 * layer, won.at("/rewards/goldReward").asInt());
            JsonNode skipped = command("reward/skip", "skip-" + layer, "{}", won.path("stateVersion").asLong());
            assertEquals(layer < 3, game.advanceToNextLayer(id, skipped.path("stateVersion").asLong(), OWNER));
            assertEquals(Math.min(3, layer + 1), sessions.get(id).getCurrentLayer());
            assertEquals(layer == 3, sessions.get(id).isCompleted());
        }
    }

    @Test void staleOrForeignCommandsStillFailWithoutPlayingOrRewarding() throws Exception {
        GameSession initial = seed(false);
        JsonNode start = command("battle/start", "start", "{}", initial.getStateVersion());
        long version = start.path("stateVersion").asLong();
        mvc.perform(post("/api/game/battle/play/" + id).header("X-Expected-State-Version", version - 1)
                        .header("X-Idempotency-Key", "stale").contentType("application/json").content("{\"handIndex\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("STATE_VERSION_CONFLICT"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("outsider", null, List.of()));
        mvc.perform(post("/api/game/battle/play/" + id).header("X-Expected-State-Version", version)
                        .header("X-Idempotency-Key", "foreign").contentType("application/json").content("{\"handIndex\":0}"))
                .andExpect(status().isForbidden());
        assertEquals(version, sessions.get(id).getStateVersion());
        assertFalse(sessions.get(id).getBattle().isRewardsHandled());
    }

    @Test void priorTerminalSnapshotCanReopenMapWithoutRerollingOrGrantingRewardsAgain() throws Exception {
        GameSession initial = seed(false);
        JsonNode start = command("battle/start", "start", "{}", initial.getStateVersion());
        JsonNode won = command("battle/play", "kill", "{\"handIndex\":0}", start.path("stateVersion").asLong());
        GameSession oldTerminal = sessions.get(id);
        oldTerminal.setMapOpen(false); // Shape persisted by the original failing path.
        sessions.put(id, oldTerminal);
        battles.handleBattleEnd(id);
        GameSession restored = sessions.get(id);
        assertTrue(restored.isMapOpen());
        assertEquals(oldTerminal.getStateVersion() + 1, restored.getStateVersion());
        assertEquals(won.path("rewards"), state().path("rewards"));
        assertEquals(oldTerminal.getPlayer().getGold(), restored.getPlayer().getGold());
        assertEquals(oldTerminal.getPlayer().getMaxHp(), restored.getPlayer().getMaxHp());
        battles.handleBattleEnd(id);
        assertEquals(restored.getStateVersion(), sessions.get(id).getStateVersion());
        verify(cards, times(1)).findByCharacterClassOrCharacterClassIsNull(CharacterClass.SUN_WUKONG);
    }

    @Test void lostCompletionReceiptKeepsUnknownAndNeverRepeatsTheCommittedPlayerCommand() throws Exception {
        GameSession initial = seed(false);
        JsonNode start = command("battle/start", "start", "{}", initial.getStateVersion());
        var failedReceipt = new RedisIdempotencyStore(new StringRedisTemplate(factory), json) {
            @Override public boolean complete(String key, Entry owner, String response, java.time.Duration ttl) {
                throw new IllegalStateException("injected: reward saved but receipt unavailable");
            }
        };
        mvc = controller(failedReceipt);
        JsonNode firstCommittedState = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/game/battle/play/" + id)
                            .header("X-Expected-State-Version", start.path("stateVersion").asLong())
                            .header("X-Idempotency-Key", "unknown-kill").contentType("application/json")
                            .content("{\"handIndex\":0}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("RESULT_UNKNOWN"));
            JsonNode current = state();
            assertTrue(current.path("victory").asBoolean());
            assertEquals(130, current.at("/player/gold").asInt());
            if (attempt == 0) firstCommittedState = current;
            else assertEquals(firstCommittedState, current, "Unknown retry must not replay a card, reroll rewards or increase version");
        }
        assertFalse(commands.find("game:battle:play:" + OWNER + ":" + id + ":unknown-kill").orElseThrow().completed());
        assertEquals(1, sessions.get(id).getBattle().getCardsPlayedThisTurn());
        verify(cards, times(1)).findByCharacterClassOrCharacterClassIsNull(CharacterClass.SUN_WUKONG);
    }
}
