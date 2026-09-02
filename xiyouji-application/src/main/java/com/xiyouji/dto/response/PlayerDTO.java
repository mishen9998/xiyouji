package com.xiyouji.dto.response;

import java.util.List;

/**
 * 玩家数据传输对象
 */
public class PlayerDTO {

    private String characterClass;
    private String displayName;
    private int hp;
    private int maxHp;
    private int gold;
    private int floor;
    private int maxEnergy;
    private int deckSize;
    private List<CardDTO> deck;
    private List<RelicDTO> relics;

    public PlayerDTO() {
    }

    public String getCharacterClass() {
        return characterClass;
    }

    public void setCharacterClass(String characterClass) {
        this.characterClass = characterClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = hp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public void setMaxHp(int maxHp) {
        this.maxHp = maxHp;
    }

    public int getGold() {
        return gold;
    }

    public void setGold(int gold) {
        this.gold = gold;
    }

    public int getFloor() {
        return floor;
    }

    public void setFloor(int floor) {
        this.floor = floor;
    }

    public int getMaxEnergy() {
        return maxEnergy;
    }

    public void setMaxEnergy(int maxEnergy) {
        this.maxEnergy = maxEnergy;
    }

    public int getDeckSize() {
        return deckSize;
    }

    public void setDeckSize(int deckSize) {
        this.deckSize = deckSize;
    }

    public List<CardDTO> getDeck() {
        return deck;
    }

    public void setDeck(List<CardDTO> deck) {
        this.deck = deck;
    }

    public List<RelicDTO> getRelics() {
        return relics;
    }

    public void setRelics(List<RelicDTO> relics) {
        this.relics = relics;
    }
}
