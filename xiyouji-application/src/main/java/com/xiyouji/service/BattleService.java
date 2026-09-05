package com.xiyouji.service;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.exception.EnemyNotFoundException;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.*;
import com.xiyouji.model.enums.*;
import com.xiyouji.port.CardRepositoryPort;
import com.xiyouji.port.EnemyRepositoryPort;
import com.xiyouji.service.session.BattleState;
import com.xiyouji.service.session.GameSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/**
 * 战斗服务 - 管理战斗流程
 * 处理战斗开始、出牌、结束回合、战斗奖励等逻辑
 */
@Service
public class BattleService {

    private static final Logger log = LoggerFactory.getLogger(BattleService.class);

    private final GameService gameService;
    private final EnemyRepositoryPort enemyRepo;
    private final CardRepositoryPort cardRepo;

    public BattleService(GameService gameService, EnemyRepositoryPort enemyRepo, CardRepositoryPort cardRepo) {
        this.gameService = gameService;
        this.enemyRepo = enemyRepo;
        this.cardRepo = cardRepo;
    }

    /** 开始战斗 */
    @Transactional
    public BattleState startBattle(String sessionId) {
        return startBattleUnderLock(sessionId);
    }

    @Transactional
    public BattleState startBattle(String sessionId, long expectedVersion, String userId) {
        return gameService.withSessionLock(sessionId, () -> {
            gameService.assertOwnerAndVersion(sessionId, userId, expectedVersion);
            return startBattleUnderLock(sessionId);
        });
    }

    private BattleState startBattleUnderLock(String sessionId) {
        GameSession session = gameService.getSession(sessionId);
        MapNode node = session.getCurrentNode();

        Enemy template = enemyRepo.findById(node.getEnemyId() != null ?
                        Long.valueOf(node.getEnemyId()) : 1L)
                .orElseThrow(() -> new EnemyNotFoundException("敌人不存在，节点enemyId: " + node.getEnemyId()));

        // 创建副本用于战斗
        Enemy enemy = new Enemy(template.getName(), template.getMaxHp(),
                template.getAttack(), template.getDefense(), template.isBoss(), template.getLevel());
        enemy.setEmoji(template.getEmoji());
        enemy.setMovePattern(template.getMovePattern() != null ?
                new ArrayList<>(template.getMovePattern()) : List.of());

        // 根据位置调整难度（越往后越难）
        int levelScalar = Math.min(node.getPosition() / 2 + 1, 10);
        enemy.setMaxHp(enemy.getMaxHp() + levelScalar * 5);
        enemy.setHp(enemy.getMaxHp());
        enemy.setAttack(enemy.getAttack() + levelScalar);

        BattleState battle = new BattleState(enemy);
        session.setBattle(battle);

        // 初始化玩家战斗状态 — HP继承上一场战斗的剩余值
        GameCharacter player = session.getPlayer();
        player.initBattle();

        // ===== 遗物效果：硬编码的老宝物（应用到 battle.getEnemy() 而非 enemy 原始对象） =====
        Enemy battleEnemy = battle.getEnemy(); // ★ 使用 BattleState 中的副本

        // 遗物效果：紧箍咒
        if (player.getRelics().stream().anyMatch(r -> GameConstants.RELIC_JINGUZHOU.equals(r.getName()))) {
            player.setStrength(player.getStrength() + 2);
        }
        // 遗物效果：定海神针
        // 兼容旧数据：如果遗物已经通过 effect 字段声明 ENERGY，就交给下面的通用解析，避免重复触发。
        boolean dinghaiHasConfiguredEnergy = player.getRelics().stream().anyMatch(r ->
                GameConstants.RELIC_DINGHAI.equals(r.getName())
                        && r.getEffect() != null
                        && r.getEffect().contains("ENERGY:"));
        if (!dinghaiHasConfiguredEnergy && player.getRelics().stream()
                .anyMatch(r -> GameConstants.RELIC_DINGHAI.equals(r.getName()))) {
            player.addBattleStartEnergy(1);
        }
        // 遗物效果：人参果
        if (player.getRelics().stream().anyMatch(r -> GameConstants.RELIC_RENSHENGUO.equals(r.getName()))) {
            player.heal(5);
        }
        // 遗物效果：照妖镜 — ★ 修复：应用到 battleEnemy 而非 enemy
        if (player.getRelics().stream().anyMatch(r -> GameConstants.RELIC_ZHAOYAOJING.equals(r.getName()))) {
            battleEnemy.addBuff(BuffType.VULNERABLE, 1);
        }

        // ===== 遗物效果：通过 effect 字段解析的新宝物 =====
        for (Relic relic : player.getRelics()) {
            if (relic.getEffect() == null || relic.getEffect().isEmpty()) continue;
            String[] parts = relic.getEffect().split(";");
            boolean isBattleStart = false;
            for (String p : parts) {
                if ("BATTLE_START".equals(p)) { isBattleStart = true; break; }
            }
            if (!isBattleStart) {
                // 非战斗开始的被动效果：MAX_HP 调整
                for (String p : parts) {
                    if (p.startsWith("MAX_HP:")) {
                        int hpChange = Integer.parseInt(p.substring(7));
                        player.setMaxHp(player.getMaxHp() + hpChange);
                        if (hpChange > 0) player.heal(hpChange);
                        else player.setHp(Math.max(1, player.getHp() + hpChange));
                    }
                }
                continue;
            }
            // 处理 BATTLE_START 效果
            for (String p : parts) {
                if (p.startsWith("WEAK:")) {
                    int turns = Integer.parseInt(p.substring(5));
                    battleEnemy.addBuff(BuffType.WEAK, turns);
                    log.info("宝物[{}]触发: 敌人+{}层虚弱", relic.getName(), turns);
                } else if (p.startsWith("VULNERABLE:")) {
                    int turns = Integer.parseInt(p.substring(11));
                    battleEnemy.addBuff(BuffType.VULNERABLE, turns);
                    log.info("宝物[{}]触发: 敌人+{}层脆弱", relic.getName(), turns);
                } else if (p.startsWith("DAMAGE:")) {
                    int dmg = Integer.parseInt(p.substring(7));
                    battleEnemy.takeDamage(dmg);
                    log.info("宝物[{}]触发: 敌人受到{}点伤害", relic.getName(), dmg);
                } else if (p.startsWith("STRENGTH:")) {
                    int str = Integer.parseInt(p.substring(9));
                    player.setStrength(player.getStrength() + str);
                    log.info("宝物[{}]触发: 玩家+{}力量", relic.getName(), str);
                } else if (p.startsWith("DEXTERITY:")) {
                    int dex = Integer.parseInt(p.substring(10));
                    player.setDexterity(player.getDexterity() + dex);
                    log.info("宝物[{}]触发: 玩家+{}敏捷", relic.getName(), dex);
                } else if (p.startsWith("BLOCK:")) {
                    int blk = Integer.parseInt(p.substring(6));
                    player.setBlock(player.getBlock() + blk);
                    log.info("宝物[{}]触发: 玩家+{}格挡", relic.getName(), blk);
                } else if (p.startsWith("GOLD:")) {
                    int gold = Integer.parseInt(p.substring(5));
                    player.setGold(player.getGold() + gold);
                    log.info("宝物[{}]触发: 玩家+{}金币", relic.getName(), gold);
                } else if (p.startsWith("ENERGY:")) {
                    int en = Integer.parseInt(p.substring(7));
                    player.addBattleStartEnergy(en);
                    log.info("宝物[{}]触发: 玩家+{}能量", relic.getName(), en);
                } else if (p.startsWith("MAX_HP:")) {
                    int hpChange = Integer.parseInt(p.substring(7));
                    player.setMaxHp(player.getMaxHp() + hpChange);
                    if (hpChange > 0) player.heal(hpChange);
                    else player.setHp(Math.max(1, player.getHp() + hpChange));
                    log.info("宝物[{}]触发: 玩家最大生命{}{}", relic.getName(), hpChange > 0 ? "+" : "", hpChange);
                }
            }
        }

        player.drawCards(GameConstants.INITIAL_HAND_SIZE);

        // 龙鳞甲：多抽一张
        if (player.getRelics().stream().anyMatch(r -> GameConstants.RELIC_LONGLINJIA.equals(r.getName()))) {
            player.drawCards(1);
        }

        // ★ 宝物回合开始效果：第一回合也需触发 TURN_START 效果（如九转金丹每回合回血、御赐琉璃盏每回合抽1张）
        for (Relic relic : player.getRelics()) {
            if (relic.getEffect() != null && relic.getEffect().contains("TURN_START")) {
                String[] parts = relic.getEffect().split(";");
                for (String p : parts) {
                    if (p.startsWith("HEAL:")) {
                        int healAmount = Integer.parseInt(p.substring(5));
                        player.heal(healAmount);
                        log.info("宝物[{}]第一回合触发: 回复{}点生命", relic.getName(), healAmount);
                    } else if (p.startsWith("DRAW:")) {
                        int drawCount = Integer.parseInt(p.substring(5));
                        player.drawCards(drawCount);
                        log.info("宝物[{}]第一回合触发: 抽{}张牌", relic.getName(), drawCount);
                    }
                }
            }
        }

        battle.startBattle();
        gameService.saveSession(session);
        log.info("战斗开始: sessionId={}, enemy={}", sessionId, enemy.getName());
        return battle;
    }

    /** 打出卡牌 — 加成通过参数传入，不修改卡牌本身 */
    @Transactional
    public BattleState playCard(String sessionId, int handIndex) {
        return playCardUnderLock(sessionId, handIndex);
    }

    public BattleState playCard(String sessionId, int handIndex, long expectedVersion, String userId) {
        return gameService.withSessionLock(sessionId, () -> {
            gameService.assertOwnerAndVersion(sessionId, userId, expectedVersion);
            return playCardUnderLock(sessionId, handIndex);
        });
    }

    private BattleState playCardUnderLock(String sessionId, int handIndex) {
        GameSession session = gameService.getSession(sessionId);
        BattleState battle = session.getBattle();
        if (battle == null) throw new InvalidActionException("不在战斗中");

        GameCharacter player = session.getPlayer();
        Card card = handIndex >= 0 && handIndex < player.getHand().size()
                ? player.getHand().get(handIndex) : null;
        if (card == null) return battle;

        int extraDmg = 0;
        int extraBlk = 0;

        // ★ 宝物被动效果：七星剑（ATTACK_BONUS）— 所有攻击牌额外伤害
        if (card.getType() == CardType.ATTACK) {
            for (Relic relic : player.getRelics()) {
                if (relic.getEffect() != null && relic.getEffect().contains("ATTACK_BONUS:")) {
                    String[] parts = relic.getEffect().split(";");
                    for (String p : parts) {
                        if (p.startsWith("ATTACK_BONUS:")) {
                            int bonus = Integer.parseInt(p.substring(13));
                            extraDmg += bonus;
                        }
                    }
                }
            }
        }

        // 龙爪：使用过技能则+5伤害
        if ("龙爪".equals(card.getName()) && battle.isSkillUsedThisTurn()) {
            extraDmg = 5;
        }
        // 致命一击：对脆弱目标三倍
        if ("致命一击".equals(card.getName())
                && battle.getEnemy().getBuffs().containsKey(BuffType.VULNERABLE)
                && battle.getEnemy().getBuffs().get(BuffType.VULNERABLE) > 0) {
            extraDmg = card.getDamage() * 2; // base+dmg + base*2 = base*3
        }
        // 破甲击：对有格挡的敌人伤害翻倍
        if ("破甲击".equals(card.getName()) && battle.getEnemy().getBlock() > 0) {
            extraDmg = card.getDamage(); // base + extra = base*2
        }
        // 金箍棒法：对脆弱目标伤害翻倍
        if ("金箍棒法".equals(card.getName())
                && battle.getEnemy().getBuffs().containsKey(BuffType.VULNERABLE)
                && battle.getEnemy().getBuffs().get(BuffType.VULNERABLE) > 0) {
            extraDmg = card.getDamage(); // base + extra = base*2
        }

        battle.playPlayerCard(player, handIndex, extraDmg, extraBlk);

        // 饕餮之口：回复等于伤害的生命值
        if ("饕餮之口".equals(card.getName())) {
            int healAmount = card.getDamage() + extraDmg;
            if (healAmount > 0) {
                player.heal(healAmount);
                battle.getCombatLog().add("🍖 饕餮之口吸血" + healAmount + "点");
            }
        }

        // 八卦炉遗物：每3张攻击牌+1力量
        if (card.getType() == CardType.ATTACK && battle.getCardsPlayedThisTurn() > 0
                && battle.getCardsPlayedThisTurn() % 3 == 0
                && player.getRelics().stream().anyMatch(r -> GameConstants.RELIC_BAGUALU.equals(r.getName()))) {
            player.setStrength(player.getStrength() + 1);
            battle.getCombatLog().add("♨️ 八卦炉触发！力量+1");
        }

        // 紫金铃遗物：每5张牌造成8伤害
        if (battle.getCardsPlayedThisTurn() > 0 && battle.getCardsPlayedThisTurn() % 5 == 0
                && player.getRelics().stream().anyMatch(r -> GameConstants.RELIC_ZIJINLING.equals(r.getName()))) {
            battle.getEnemy().takeDamage(8);
            battle.getCombatLog().add("🔔 紫金铃触发！造成 8 点伤害");
        }

        gameService.saveSession(session);
        log.debug("打出卡牌: sessionId={}, card={}", sessionId, card.getName());
        return battle;
    }

    /** 结束回合 */
    @Transactional
    public BattleState endTurn(String sessionId) {
        return endTurnUnderLock(sessionId);
    }

    public BattleState endTurn(String sessionId, long expectedVersion, String userId) {
        return gameService.withSessionLock(sessionId, () -> {
            gameService.assertOwnerAndVersion(sessionId, userId, expectedVersion);
            return endTurnUnderLock(sessionId);
        });
    }

    private BattleState endTurnUnderLock(String sessionId) {
        GameSession session = gameService.getSession(sessionId);
        BattleState battle = session.getBattle();
        if (battle == null) throw new InvalidActionException("不在战斗中");

        battle.endPlayerTurn(session.getPlayer());

        // ★ 宝物回合开始效果：九转金丹（TURN_START;HEAL:N）、御赐琉璃盏（TURN_START;DRAW:N）
        for (Relic relic : session.getPlayer().getRelics()) {
            if (relic.getEffect() != null && relic.getEffect().contains("TURN_START")) {
                String[] parts = relic.getEffect().split(";");
                for (String p : parts) {
                    if (p.startsWith("HEAL:")) {
                        int healAmount = Integer.parseInt(p.substring(5));
                        session.getPlayer().heal(healAmount);
                        battle.getCombatLog().add("💊 " + relic.getName() + "触发！回复" + healAmount + "点生命");
                    } else if (p.startsWith("DRAW:")) {
                        int drawCount = Integer.parseInt(p.substring(5));
                        session.getPlayer().drawCards(drawCount);
                        battle.getCombatLog().add("🍶 " + relic.getName() + "触发！抽" + drawCount + "张牌");
                    }
                }
            }
        }

        gameService.saveSession(session);
        log.debug("结束回合: sessionId={}, turn={}", sessionId, battle.getTurnNumber());
        return battle;
    }

    /** Executes a card command and resolves rewards while retaining the session lock. */
    @Transactional
    public Map<String, Object> playCardAndResolve(String sessionId, int handIndex,
                                                   long expectedVersion, String userId) {
        return gameService.withSessionLock(sessionId, () -> {
            gameService.assertOwnerAndVersion(sessionId, userId, expectedVersion);
            playCardUnderLock(sessionId, handIndex);
            GameSession session = gameService.getSession(sessionId);
            if (session.getBattle() != null && session.getBattle().isBattleOver()) {
                Map<String, Object> rewards = handleBattleEndUnderLock(sessionId);
                session.setMapOpen(true);
                gameService.saveSession(session);
                Map<String, Object> info = getBattleInfo(sessionId);
                info.put("rewards", rewards);
                return info;
            }
            return getBattleInfo(sessionId);
        });
    }

    /** Executes a turn command and resolves rewards while retaining the session lock. */
    @Transactional
    public Map<String, Object> endTurnAndResolve(String sessionId, long expectedVersion, String userId) {
        return gameService.withSessionLock(sessionId, () -> {
            gameService.assertOwnerAndVersion(sessionId, userId, expectedVersion);
            endTurnUnderLock(sessionId);
            GameSession session = gameService.getSession(sessionId);
            if (session.getBattle() != null && session.getBattle().isBattleOver()) {
                Map<String, Object> rewards = handleBattleEndUnderLock(sessionId);
                session.setMapOpen(true);
                gameService.saveSession(session);
                Map<String, Object> info = getBattleInfo(sessionId);
                info.put("rewards", rewards);
                return info;
            }
            return getBattleInfo(sessionId);
        });
    }

    /** 战斗结束处理 */
    @Transactional
    public Map<String, Object> handleBattleEnd(String sessionId) {
        return gameService.withSessionLock(sessionId, () -> handleBattleEndUnderLock(sessionId));
    }

    private Map<String, Object> handleBattleEndUnderLock(String sessionId) {
        GameSession session = gameService.getSession(sessionId);
        BattleState battle = session.getBattle();
        if (battle == null || !battle.isBattleOver()) {
            throw new InvalidActionException("战斗未结束");
        }
        // 防止重复领取奖励
        if (battle.isRewardsHandled()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("alreadyHandled", true);
            return empty;
        }
        battle.setRewardsHandled(true);

        Map<String, Object> result = new HashMap<>();
        GameCharacter player = session.getPlayer();

        if (battle.isVictory()) {
            // 金币奖励：基础 + 层数加成 + Boss额外
            int goldReward = GameConstants.BASE_GOLD_REWARD
                    + session.getCurrentLayer() * GameConstants.GOLD_PER_LAYER;
            if (battle.getEnemy().isBoss()) goldReward += GameConstants.BOSS_GOLD_BONUS;
            // ★ 太宗玉玺：战斗金币奖励翻倍
            if (player.getRelics().stream().anyMatch(r -> GameConstants.RELIC_EMPEROR_JADE_SEAL.equals(r.getName()))) {
                goldReward *= 2;
            }
            player.setGold(player.getGold() + goldReward);
            result.put("victory", true);
            result.put("goldReward", goldReward);

            // 卡牌奖励：战斗必掉 CARD_REWARD_COUNT 张选1
            List<Card> rewards = gameService.getCardRewards(sessionId, GameConstants.CARD_REWARD_COUNT);
            battle.setCardRewards(rewards);
            result.put("cardRewards", rewards);

            // 宝物掉落：RELIC_DROP_RATE概率掉落遗物（Boss必掉）
            boolean dropRelic = battle.getEnemy().isBoss()
                    || new Random().nextDouble() < GameConstants.RELIC_DROP_RATE;
            if (dropRelic) {
                Relic relic = gameService.getRandomRelic(sessionId);
                if (relic != null) {
                    player.getRelics().add(relic);
                    result.put("relicReward", relic);
                }
            }

            log.info("战斗胜利: sessionId={}, goldReward={}, dropRelic={}",
                    sessionId, goldReward, dropRelic);

            // ★ 宝物击杀效果：炼妖壶（ON_KILL;MAX_HP:N）— 每击杀敌人最大生命+N
            for (Relic relic : player.getRelics()) {
                if (relic.getEffect() != null && relic.getEffect().contains("ON_KILL")) {
                    String[] parts = relic.getEffect().split(";");
                    for (String p : parts) {
                        if (p.startsWith("MAX_HP:")) {
                            int hpBonus = Integer.parseInt(p.substring(7));
                            player.setMaxHp(player.getMaxHp() + hpBonus);
                            player.heal(hpBonus);
                            result.merge("onKillMsg", relic.getName() + "触发！最大生命+" + hpBonus, (a, b) -> a + "; " + b);
                        }
                    }
                }
            }
        } else {
            result.put("victory", false);
            result.put("message", "你已被击败，请重新开始！");
            log.info("战斗失败: sessionId={}", sessionId);
        }

        gameService.saveSession(session);
        return result;
    }

    /** Chooses one reward card under the owner/session lock. */
    @Transactional
    public Map<String, Object> chooseCardReward(String sessionId, int cardIndex,
                                                long expectedVersion, String userId) {
        return gameService.withSessionLock(sessionId, () -> {
            gameService.assertOwnerAndVersion(sessionId, userId, expectedVersion);
            GameSession session = gameService.getSession(sessionId);
            BattleState battle = session.getBattle();
            Map<String, Object> result = new HashMap<>();
            if (battle != null && battle.getCardRewards() != null
                    && cardIndex >= 0 && cardIndex < battle.getCardRewards().size()) {
                Card selected = battle.getCardRewards().get(cardIndex);
                session.getPlayer().addCard(selected.copy());
                result.put("chosenCard", selected.getName());
                // Consuming the reward list makes a successful command safe even if
                // an old client retries with a different idempotency key.
                battle.setCardRewards(null);
            }
            result.put("success", true);
            result.put("stateVersion", session.getStateVersion() + 1);
            result.put("player", session.getPlayer());
            gameService.saveSession(session);
            result.put("stateVersion", session.getStateVersion());
            return result;
        });
    }

    @Transactional
    public Map<String, Object> skipReward(String sessionId, long expectedVersion, String userId) {
        return gameService.withSessionLock(sessionId, () -> {
            gameService.assertOwnerAndVersion(sessionId, userId, expectedVersion);
            GameSession session = gameService.getSession(sessionId);
            if (session.getBattle() != null) session.getBattle().setCardRewards(null);
            gameService.saveSession(session);
            return Map.of("success", true, "stateVersion", session.getStateVersion());
        });
    }

    /** 获取战斗状态（JSON友好格式） */
    public Map<String, Object> getBattleInfo(String sessionId) {
        GameSession session = gameService.getSession(sessionId);
        BattleState battle = session.getBattle();
        GameCharacter player = session.getPlayer();

        Map<String, Object> info = new HashMap<>();
        info.put("stateVersion", session.getStateVersion());
        if (battle == null) {
            info.put("inBattle", false);
            return info;
        }

        info.put("inBattle", true);
        info.put("turnNumber", battle.getTurnNumber());
        info.put("playerTurn", battle.isPlayerTurn());
        info.put("battleOver", battle.isBattleOver());
        info.put("victory", battle.isVictory());

        // 玩家信息
        Map<String, Object> playerInfo = new HashMap<>();
        playerInfo.put("characterClass", player.getCharacterClass().name());
        playerInfo.put("displayName", player.getCharacterClass().getDisplayName());
        playerInfo.put("name", player.getCharacterClass().getDisplayName());
        playerInfo.put("hp", player.getHp());
        playerInfo.put("maxHp", player.getMaxHp());
        playerInfo.put("block", player.getBlock());
        playerInfo.put("energy", player.getEnergy());
        playerInfo.put("maxEnergy", player.getCurrentMaxEnergy());
        playerInfo.put("strength", player.getStrength());
        playerInfo.put("dexterity", player.getDexterity());
        playerInfo.put("emoji", player.getEmoji() != null ? player.getEmoji() :
                player.getCharacterClass().name());
        playerInfo.put("gold", player.getGold());
        playerInfo.put("floor", player.getFloor());

        // 遗物列表
        List<Map<String, Object>> relicList = new ArrayList<>();
        for (Relic r : player.getRelics()) {
            Map<String, Object> rm = new HashMap<>();
            rm.put("name", r.getName());
            rm.put("description", r.getDescription());
            rm.put("emoji", r.getEmoji());
            relicList.add(rm);
        }
        playerInfo.put("relics", relicList);

        // 抽牌堆内容（供前端查看）
        List<Map<String, Object>> drawPile = new ArrayList<>();
        for (Card c : player.getDrawPile()) {
            drawPile.add(cardSummary(c));
        }
        playerInfo.put("drawPile", drawPile);

        // 弃牌堆内容
        List<Map<String, Object>> discardPile = new ArrayList<>();
        for (Card c : player.getDiscardPile()) {
            discardPile.add(cardSummary(c));
        }
        playerInfo.put("discardPile", discardPile);

        // 消耗堆
        List<Map<String, Object>> exhaustPile = new ArrayList<>();
        for (Card c : player.getExhaustPile()) {
            exhaustPile.add(cardSummary(c));
        }
        playerInfo.put("exhaustPile", exhaustPile);

        // 手牌
        List<Map<String, Object>> hand = new ArrayList<>();
        for (int i = 0; i < player.getHand().size(); i++) {
            Card c = player.getHand().get(i);
            hand.add(cardToMap(c, i));
        }
        playerInfo.put("hand", hand);
        playerInfo.put("drawPileSize", player.getDrawPile().size());
        playerInfo.put("discardPileSize", player.getDiscardPile().size());

        // Buffs — 包含永久buff(力量/敏捷)和临时buff(带回合数)
        List<Map<String, Object>> playerBuffs = new ArrayList<>();
        if (player.getStrength() > 0) {
            Map<String, Object> b = new HashMap<>();
            b.put("name", "力量");
            b.put("value", player.getStrength());
            b.put("permanent", true);
            playerBuffs.add(b);
        }
        if (player.getDexterity() > 0) {
            Map<String, Object> b = new HashMap<>();
            b.put("name", "敏捷");
            b.put("value", player.getDexterity());
            b.put("permanent", true);
            playerBuffs.add(b);
        }
        for (Map.Entry<BuffType, Integer> e : player.getBuffs().entrySet()) {
            Map<String, Object> b = new HashMap<>();
            b.put("name", e.getKey().getDisplayName());
            b.put("value", e.getValue());
            b.put("permanent", false);
            playerBuffs.add(b);
        }
        playerInfo.put("buffs", playerBuffs);

        info.put("player", playerInfo);

        // 敌人信息
        Map<String, Object> enemyInfo = new HashMap<>();
        enemyInfo.put("name", battle.getEnemy().getName());
        enemyInfo.put("hp", battle.getEnemy().getHp());
        enemyInfo.put("maxHp", battle.getEnemy().getMaxHp());
        enemyInfo.put("block", battle.getEnemy().getBlock());
        enemyInfo.put("strength", battle.getEnemy().getStrength());
        enemyInfo.put("emoji", battle.getEnemy().getEmoji());
        enemyInfo.put("intent", battle.getEnemy().getIntent().name());
        enemyInfo.put("intentValue", battle.getEnemy().getIntentValue());
        enemyInfo.put("isBoss", battle.getEnemy().isBoss());

        // 敌人Buffs — 包含永久buff和临时buff
        List<Map<String, Object>> enemyBuffsList = new ArrayList<>();
        if (battle.getEnemy().getStrength() > 0) {
            Map<String, Object> b = new HashMap<>();
            b.put("name", "力量");
            b.put("value", battle.getEnemy().getStrength());
            b.put("permanent", true);
            enemyBuffsList.add(b);
        }
        for (Map.Entry<BuffType, Integer> e : battle.getEnemy().getBuffs().entrySet()) {
            Map<String, Object> b = new HashMap<>();
            b.put("name", e.getKey().getDisplayName());
            b.put("value", e.getValue());
            b.put("permanent", false);
            enemyBuffsList.add(b);
        }
        enemyInfo.put("buffs", enemyBuffsList);

        info.put("enemy", enemyInfo);

        // 战斗日志（最新的15条）
        List<String> combatLog = battle.getCombatLog();
        info.put("combatLog", combatLog.subList(Math.max(0, combatLog.size() - 15), combatLog.size()));

        return info;
    }

    private Map<String, Object> cardToMap(Card card, int index) {
        Map<String, Object> m = cardSummary(card);
        m.put("index", index);
        m.put("description", card.getDescription());
        m.put("drawCards", card.getDrawCards());
        return m;
    }

    private Map<String, Object> cardSummary(Card card) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", card.getName());
        m.put("type", card.getType().name());
        m.put("cost", card.getCost());
        m.put("damage", card.getDamage());
        m.put("block", card.getBlock());
        m.put("emoji", card.getEmoji());
        m.put("rarity", card.getRarity().name());
        m.put("upgraded", card.isUpgraded());
        m.put("exhaust", card.isExhaust());
        return m;
    }
}
