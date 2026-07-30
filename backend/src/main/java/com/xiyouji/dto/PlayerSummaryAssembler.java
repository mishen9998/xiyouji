package com.xiyouji.dto;

import com.xiyouji.dto.response.CardDTO;
import com.xiyouji.dto.response.PlayerDTO;
import com.xiyouji.dto.response.RelicDTO;
import com.xiyouji.model.Card;
import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.Relic;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家摘要装配器
 * 将 GameCharacter 领域对象装配为 PlayerDTO 响应对象，供多个 Controller 共用。
 *
 * 抽取自 GameController#playerSummary 与 BattleController#playerSummary 的重复实现，
 * 保证玩家摘要的 JSON 契约在所有接口中一致。
 */
@Component
public class PlayerSummaryAssembler {

    /**
     * 装配玩家摘要
     *
     * @param p 玩家角色领域对象
     * @return PlayerDTO 仅包含前端展示所需字段，null 字段不序列化
     */
    public PlayerDTO toPlayerSummary(GameCharacter p) {
        PlayerDTO dto = new PlayerDTO();
        dto.setCharacterClass(p.getCharacterClass().name());
        dto.setDisplayName(p.getCharacterClass().getDisplayName());
        dto.setHp(p.getHp());
        dto.setMaxHp(p.getMaxHp());
        dto.setGold(p.getGold());
        dto.setFloor(p.getFloor());
        dto.setMaxEnergy(p.getMaxEnergy());
        dto.setDeckSize(p.getDeck().size());
        dto.setDeck(toCardDTOList(p.getDeck()));
        dto.setRelics(toRelicDTOList(p.getRelics()));
        return dto;
    }

    private List<CardDTO> toCardDTOList(List<Card> deck) {
        List<CardDTO> list = new ArrayList<>(deck.size());
        for (Card c : deck) {
            CardDTO cd = new CardDTO();
            cd.setName(c.getName());
            cd.setType(c.getType().name());
            cd.setCost(c.getCost());
            cd.setEmoji(c.getEmoji());
            cd.setUpgraded(c.isUpgraded());
            cd.setDamage(c.getDamage());
            cd.setBlock(c.getBlock());
            cd.setDrawCards(c.getDrawCards());
            list.add(cd);
        }
        return list;
    }

    private List<RelicDTO> toRelicDTOList(List<Relic> relics) {
        List<RelicDTO> list = new ArrayList<>(relics.size());
        for (Relic r : relics) {
            RelicDTO rd = new RelicDTO();
            rd.setName(r.getName());
            rd.setDescription(r.getDescription());
            rd.setEmoji(r.getEmoji());
            rd.setTier(r.getTier().name());
            list.add(rd);
        }
        return list;
    }
}
