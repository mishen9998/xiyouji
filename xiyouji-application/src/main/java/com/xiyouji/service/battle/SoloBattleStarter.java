package com.xiyouji.service.battle;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.exception.EnemyNotFoundException;
import com.xiyouji.model.Enemy;
import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.MapNode;
import com.xiyouji.model.Relic;
import com.xiyouji.model.enums.BuffType;
import com.xiyouji.port.EnemyRepositoryPort;
import com.xiyouji.service.GameService;
import com.xiyouji.service.session.BattleState;
import com.xiyouji.service.session.GameSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 单人战斗开场组件
 *
 * 负责：敌人创建与位置难度缩放、玩家战斗状态初始化、
 * 遗物战斗开始效果（硬编码老宝物 + effect 字段解析）、初始抽牌与首回合 TURN_START 效果。
 */
@Component
public class SoloBattleStarter {

    private static final Logger log = LoggerFactory.getLogger(SoloBattleStarter.class);

    private final GameService gameService;
    private final EnemyRepositoryPort enemyRepo;
    private final SoloRelicTriggers relicTriggers;

    public SoloBattleStarter(GameService gameService, EnemyRepositoryPort enemyRepo, SoloRelicTriggers relicTriggers) {
        this.gameService = gameService;
        this.enemyRepo = enemyRepo;
        this.relicTriggers = relicTriggers;
    }

    public BattleState start(String sessionId) {
        GameSession session = gameService.getSession(sessionId);
        MapNode node = session.getCurrentNode();

        Enemy template = EnemyBehaviorGuard.read(() -> enemyRepo.findById(node.getEnemyId() != null ?
                        Long.valueOf(node.getEnemyId()) : 1L))
                .orElseThrow(() -> new EnemyNotFoundException("敌人不存在，节点enemyId: " + node.getEnemyId()));

        // 创建副本用于战斗
        Enemy enemy = template.copy();
        enemy.setEncounterId(node.getId());
        EnemyBehaviorGuard.validate(enemy);

        // 根据位置调整难度（越往后越难）
        int levelScalar = Math.min(node.getPosition() / 2 + 1, 10);
        enemy.setMaxHp(enemy.getMaxHp() + levelScalar * 5);
        enemy.setHp(enemy.getMaxHp());
        enemy.setAttack(enemy.getAttack() + levelScalar);

        BattleState battle = new BattleState(enemy);
        session.setBattle(battle);

        // 初始化玩家战斗状态 — HP继承上一场战斗的剩余值
        GameCharacter player = session.getPlayer();
        // 兼容旧会话：旧版本曾把战斗内遗物加成写入 maxEnergy，进入新战斗时恢复基础值。
        player.setMaxEnergy(GameConstants.MAX_ENERGY);
        player.initBattle();
        player.addBlock(session.getNextBattleBlock());
        session.setNextBattleBlock(0);

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
        relicTriggers.applyTurnStartEffects(player,
                (relic, kind, desc) -> log.info("宝物[{}]第一回合触发: {}", relic.getName(), desc));

        battle.startBattle(session.getOwnerUserId());
        gameService.saveSession(session);
        log.info("战斗开始: sessionId={}, enemy={}", sessionId, enemy.getName());
        return battle;
    }
}
