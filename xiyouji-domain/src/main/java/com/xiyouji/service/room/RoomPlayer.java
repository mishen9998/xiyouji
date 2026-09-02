package com.xiyouji.service.room;

import com.xiyouji.model.Card;
import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.Relic;
import com.xiyouji.model.enums.CharacterClass;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 房间内的玩家信息
 * 多人协作PvE中，每个加入房间的玩家对应一个 RoomPlayer。
 * 注意：手牌在战斗开始后才生成，存于 MultiplayerBattleState 而非此处。
 */
public class RoomPlayer implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 玩家ID（来自 User 实体或游客 token 的 subject） */
    private String userId;

    /** 玩家显示名 */
    private String username;

    /** 选择的角色职业（未选时为 null） */
    private CharacterClass characterClass;

    /** 是否已准备 */
    private boolean ready;

    /** 是否为房主 */
    private boolean host;

    /** 当前HP（地图探索阶段持久化） */
    private int hp;

    /** 最大HP */
    private int maxHp;

    /** 金币 */
    private int gold;

    /** 牌组（跨战斗持久化） */
    private List<Card> deck = new ArrayList<>();

    /** 遗物列表 */
    private List<Relic> relics = new ArrayList<>();

    public RoomPlayer() {
    }

    public RoomPlayer(String userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public CharacterClass getCharacterClass() { return characterClass; }
    public void setCharacterClass(CharacterClass characterClass) { this.characterClass = characterClass; }

    public boolean isReady() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }

    public boolean isHost() { return host; }
    public void setHost(boolean host) { this.host = host; }

    public int getHp() { return hp; }
    public void setHp(int hp) { this.hp = hp; }

    public int getMaxHp() { return maxHp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }

    public int getGold() { return gold; }
    public void setGold(int gold) { this.gold = gold; }

    public List<Card> getDeck() { return deck; }
    public void setDeck(List<Card> deck) { this.deck = deck; }

    public List<Relic> getRelics() { return relics; }
    public void setRelics(List<Relic> relics) { this.relics = relics; }

    /** 从 GameCharacter 同步持久化状态（战斗结束后调用） */
    public void syncFromCharacter(GameCharacter gc) {
        this.hp = gc.getHp();
        this.maxHp = gc.getMaxHp();
        this.gold = gc.getGold();
        this.deck = new ArrayList<>(gc.getDeck());
        this.relics = new ArrayList<>(gc.getRelics());
    }
}
