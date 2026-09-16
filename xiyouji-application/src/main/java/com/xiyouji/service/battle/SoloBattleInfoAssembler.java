package com.xiyouji.service.battle;

import com.xiyouji.model.Card;
import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.Relic;
import com.xiyouji.model.enums.BuffType;
import com.xiyouji.service.session.BattleState;
import com.xiyouji.service.session.GameSession;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单人战斗状态组装组件
 *
 * 将战斗会话转为 JSON 友好的 Map 结构，供 HTTP 响应与前端渲染。
 */
@Component
public class SoloBattleInfoAssembler {

    /** 获取战斗状态（JSON友好格式） */
    public Map<String, Object> toBattleInfo(GameSession session) {
        BattleState battle = session.getBattle();
        GameCharacter player = session.getPlayer();

        Map<String, Object> info = new HashMap<>();
        info.put("stateVersion", session.getStateVersion());
        if (battle == null) {
            info.put("inBattle", false);
            return info;
        }

        info.put("inBattle", true);
        info.put("storyEvent",com.xiyouji.service.event.StoryCatalog.battle(session.getSessionId() + ":" +
            (session.getCurrentNode() == null ? "battle" : session.getCurrentNode().getId()),session.getCurrentLayer(),
            battle.getEnemy(),battle.isBattleOver(),battle.isVictory()));
        info.put("rulesVersion", EnemyForecastInfo.rulesVersion(battle.getEnemy()));
        info.put("turnNumber", battle.getTurnNumber());
        info.put("playerTurn", battle.isPlayerTurn());
        info.put("battleOver", battle.isBattleOver());
        info.put("victory", battle.isVictory());
        if (battle.isRewardsHandled()) info.put("rewards", rewardInfo(battle));

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
        EnemyForecastInfo.append(enemyInfo, battle.getEnemy(), Map.of(battle.getPlayerUserId(), player));

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

    private Map<String, Object> rewardInfo(BattleState battle) {
        Map<String, Object> result = new HashMap<>(battle.getRewardSummary());
        result.put("cardRewards", battle.getCardRewards() == null ? List.of() : battle.getCardRewards());
        result.put("resolved", battle.isRewardsHandled() && battle.getCardRewards() == null);
        return result;
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
