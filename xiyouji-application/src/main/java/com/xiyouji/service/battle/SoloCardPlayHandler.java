package com.xiyouji.service.battle;

import com.xiyouji.constants.GameConstants;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.Card;
import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.Relic;
import com.xiyouji.model.enums.BuffType;
import com.xiyouji.model.enums.CardType;
import com.xiyouji.service.GameService;
import com.xiyouji.service.session.BattleState;
import com.xiyouji.service.session.GameSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 单人战斗出牌组件
 *
 * 负责：出牌前置校验、遗物被动加成（七星剑）、特殊卡牌加成（龙爪/致命一击/破甲击/金箍棒法）、
 * 卡牌结算与打出后遗物效果（饕餮之口/八卦炉/紫金铃）。
 */
@Component
public class SoloCardPlayHandler {

    private static final Logger log = LoggerFactory.getLogger(SoloCardPlayHandler.class);

    private final GameService gameService;

    public SoloCardPlayHandler(GameService gameService) {
        this.gameService = gameService;
    }

    /** 打出卡牌 — 加成通过参数传入，不修改卡牌本身 */
    public BattleState play(String sessionId, int handIndex) {
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
}