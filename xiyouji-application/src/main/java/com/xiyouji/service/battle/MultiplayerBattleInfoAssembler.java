package com.xiyouji.service.battle;

import com.xiyouji.model.Card;
import com.xiyouji.model.Enemy;
import com.xiyouji.model.GameCharacter;
import com.xiyouji.service.room.MultiplayerBattleState;
import com.xiyouji.service.room.MultiplayerPlayer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 战斗状态组装器
 * 将 MultiplayerBattleState 转为 JSON 友好的 Map 结构（供 WebSocket 广播和 HTTP 响应），
 * 纯函数、无状态、无持久化依赖。
 */
@Component
public class MultiplayerBattleInfoAssembler {

    /**
     * 将战斗状态转为JSON友好的Map结构
     * 包含所有玩家的手牌信息（支持队友间卡牌可见，便于协作）
     */
    public Map<String, Object> toBattleInfo(MultiplayerBattleState state) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("roomCode", state.getRoomCode());
        info.put("stateVersion", state.getStateVersion());
        info.put("turnNumber", state.getTurnNumber());
        info.put("playerTurn", state.isPlayerTurn());
        info.put("battleOver", state.isBattleOver());
        info.put("victory", state.isVictory());
        info.put("storyEvent",com.xiyouji.service.event.StoryCatalog.battle(
            state.getStoryInstanceId() == null ? state.getRoomCode() + ":legacy" : state.getStoryInstanceId(),
            state.getStoryFloor(),state.getEnemy(),state.isBattleOver(),state.isVictory()));
        info.put("rulesVersion", EnemyForecastInfo.rulesVersion(state.getEnemy()));

        // 敌人信息
        Enemy enemy = state.getEnemy();
        Map<String, Object> enemyInfo = new LinkedHashMap<>();
        enemyInfo.put("name", enemy.getName());
        enemyInfo.put("hp", enemy.getHp());
        enemyInfo.put("maxHp", enemy.getMaxHp());
        enemyInfo.put("block", enemy.getBlock());
        enemyInfo.put("strength", enemy.getStrength());
        enemyInfo.put("emoji", enemy.getEmoji());
        enemyInfo.put("intent", enemy.getIntent() != null ? enemy.getIntent().name() : "ATTACK");
        enemyInfo.put("intentValue", enemy.getIntentValue());
        enemyInfo.put("isBoss", enemy.isBoss());
        enemyInfo.put("buffs", enemy.getBuffs() != null ? enemy.getBuffs() : Map.of());
        enemyInfo.put("targetPlayerIndex", state.getTargetPlayerIndex());
        Map<String, GameCharacter> forecastPlayers = new LinkedHashMap<>();
        state.getPlayers().stream().filter(MultiplayerPlayer::isAlive).forEach(p -> forecastPlayers.put(p.getUserId(), p.getCharacter()));
        EnemyForecastInfo.append(enemyInfo, enemy, forecastPlayers);
        info.put("enemy", enemyInfo);

        // 玩家信息列表
        List<Map<String, Object>> playerList = new ArrayList<>();
        for (int i = 0; i < state.getPlayers().size(); i++) {
            MultiplayerPlayer p = state.getPlayers().get(i);
            GameCharacter gc = p.getCharacter();
            Map<String, Object> pInfo = new LinkedHashMap<>();
            pInfo.put("index", i);
            pInfo.put("userId", p.getUserId());
            pInfo.put("username", p.getUsername());
            pInfo.put("characterClass", gc.getCharacterClass() != null ? gc.getCharacterClass().name() : null);
            pInfo.put("hp", gc.getHp());
            pInfo.put("maxHp", gc.getMaxHp());
            pInfo.put("block", gc.getBlock());
            pInfo.put("energy", gc.getEnergy());
            pInfo.put("maxEnergy", gc.getCurrentMaxEnergy());
            pInfo.put("strength", gc.getStrength());
            pInfo.put("dexterity", gc.getDexterity());
            pInfo.put("endedTurn", p.isEndedTurn());
            pInfo.put("alive", p.isAlive());
            pInfo.put("deckSize", gc.getDeck() != null ? gc.getDeck().size() : 0);
            pInfo.put("drawPileSize", gc.getDrawPile() != null ? gc.getDrawPile().size() : 0);
            pInfo.put("discardPileSize", gc.getDiscardPile() != null ? gc.getDiscardPile().size() : 0);
            pInfo.put("buffs", gc.getBuffs() != null ? gc.getBuffs() : Map.of());

            // 手牌详情（含索引，支持队友间可见便于协作）
            List<Map<String, Object>> hand = new ArrayList<>();
            if (gc.getHand() != null) {
                for (int j = 0; j < gc.getHand().size(); j++) {
                    Card card = gc.getHand().get(j);
                    Map<String, Object> cardInfo = new LinkedHashMap<>();
                    cardInfo.put("index", j);
                    cardInfo.put("name", card.getName());
                    cardInfo.put("type", card.getType() != null ? card.getType().name() : null);
                    cardInfo.put("cost", card.getCost());
                    cardInfo.put("damage", card.getDamage());
                    cardInfo.put("block", card.getBlock());
                    cardInfo.put("emoji", card.getEmoji());
                    cardInfo.put("exhaust", card.isExhaust());
                    cardInfo.put("description", card.getDescription());
                    hand.add(cardInfo);
                }
            }
            pInfo.put("hand", hand);
            playerList.add(pInfo);
        }
        info.put("players", playerList);
        info.put("alivePlayerCount", state.alivePlayerCount());
        info.put("playersEndedTurn", state.getPlayers().stream()
                .filter(MultiplayerPlayer::isEndedTurn).count());

        // 奖励阶段信息
        info.put("rewardsPhase", state.isRewardsPhase());
        info.put("rewardsHandled", state.isRewardsHandled());
        if (state.isRewardsPhase() && !state.getRewards().isEmpty()) {
            // 每个玩家的奖励选项（只发自己的，避免作弊）
            Map<String, Object> rewardsInfo = new LinkedHashMap<>();
            for (Map.Entry<String, List<Card>> entry : state.getRewards().entrySet()) {
                List<Map<String, Object>> cardList = new ArrayList<>();
                for (Card c : entry.getValue()) {
                    Map<String, Object> ci = new LinkedHashMap<>();
                    ci.put("name", c.getName());
                    ci.put("type", c.getType() != null ? c.getType().name() : null);
                    ci.put("cost", c.getCost());
                    ci.put("damage", c.getDamage());
                    ci.put("block", c.getBlock());
                    ci.put("emoji", c.getEmoji());
                    ci.put("description", c.getDescription());
                    cardList.add(ci);
                }
                rewardsInfo.put(entry.getKey(), cardList);
            }
            info.put("rewards", rewardsInfo);
            info.put("claimedRewards", state.getClaimedRewards());
        }

        // 战斗日志（最近20条）
        List<String> log = state.getCombatLog();
        int start = Math.max(0, log.size() - 20);
        info.put("combatLog", log.subList(start, log.size()));

        return info;
    }
}
