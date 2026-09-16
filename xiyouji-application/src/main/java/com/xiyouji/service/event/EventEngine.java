package com.xiyouji.service.event;

import com.xiyouji.dto.response.EventPreview;
import com.xiyouji.event.EventDefinition;
import com.xiyouji.event.EventState;
import com.xiyouji.exception.InvalidActionException;
import com.xiyouji.model.Card;
import com.xiyouji.model.MapNode;
import com.xiyouji.model.enums.Rarity;
import com.xiyouji.model.enums.RelicTier;
import com.xiyouji.port.CardRepositoryPort;
import com.xiyouji.port.RelicRepositoryPort;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class EventEngine {
    private final CardRepositoryPort cards;
    private final RelicRepositoryPort relics;
    public EventEngine(CardRepositoryPort cards, RelicRepositoryPort relics) {
        this.cards = cards; this.relics = relics;
    }
    /** Caller persists this snapshot before any choice is accepted. */
    public EventState create(String aggregateId, MapNode node, List<EventActor> actors) {
        EventState state = new EventState();
        state.eventInstanceId = aggregateId + ":" + node.getId();
        state.definitionId = EventCatalog.forNode(node).id();
        for (EventActor actor : actors) {
            List<EventState.Outcome> outcomes = new ArrayList<>();
            for (var option : EventCatalog.get(state.definitionId).options()) {
                outcomes.add(lock(state.eventInstanceId, actor, option));
            }
            state.members.put(actor.userId(), outcomes);
        }
        return state;
    }
    private EventState.Outcome lock(String id, EventActor actor, EventDefinition.Option option) {
        var player = actor.character();
        var outcome = new EventState.Outcome();
        outcome.hpCost = option.hpCost() + percent(player.getMaxHp(), option.hpPercent());
        outcome.goldCost = option.goldCost(); outcome.goldReward = option.goldReward();
        outcome.reward = option.reward(); outcome.amount = option.amount();
        Random random = new Random(Objects.hash(id, actor.userId(), option.id()));
        switch (option.reward()) {
            case HEAL, HEAL_TO -> outcome.amount = percent(player.getMaxHp(), option.amount());
            case UPGRADE, REMOVE_BASIC, REMOVE_NON_BASIC -> {
                List<Integer> indices = new ArrayList<>();
                for (int i = 0; i < player.getDeck().size(); i++) {
                    Card card = player.getDeck().get(i);
                    boolean legal = switch (option.reward()) {
                        case UPGRADE -> canUpgrade(card);
                        case REMOVE_BASIC -> card.getRarity() == Rarity.BASIC;
                        default -> card.getRarity() != null && card.getRarity() != Rarity.BASIC;
                    };
                    if (legal) indices.add(i);
                }
                if (indices.isEmpty()) outcome.unavailableReason = "没有合法的卡牌目标";
                else {
                    outcome.cardIndex = indices.get(random.nextInt(indices.size()));
                    Card card = player.getDeck().get(outcome.cardIndex);
                    outcome.cardFingerprint = fingerprint(card);
                    outcome.targetName = card.getName();
                }
            }
            case CARD -> {
                var available = cards.findByCharacterClassOrCharacterClassIsNull(player.getCharacterClass()).stream()
                    .filter(c -> c.getRarity() == Rarity.COMMON)
                    .sorted(Comparator.comparing(Card::getName)).toList();
                if (available.isEmpty()) outcome.unavailableReason = "没有可获得的普通卡牌";
                else { outcome.card = available.get(random.nextInt(available.size())).copy(); outcome.targetName = outcome.card.getName(); }
            }
            case RELIC -> {
                var available = relics.findAll().stream().filter(r -> r.getTier() == RelicTier.COMMON)
                    .filter(r -> r.getCharacterClass() == null || r.getCharacterClass() == player.getCharacterClass())
                    .filter(r -> player.getRelics().stream().noneMatch(owned -> Objects.equals(owned.getName(), r.getName())))
                    .sorted(Comparator.comparing(com.xiyouji.model.Relic::getName)).toList();
                if (available.isEmpty()) outcome.unavailableReason = "没有可获得的普通遗物";
                else { outcome.relic = available.get(random.nextInt(available.size())); outcome.targetName = outcome.relic.getName(); }
            }
            default -> { }
        }
        return outcome;
    }
    public static int percent(int maxHp, int percent) { return (int) (((long) maxHp * percent + 99) / 100); }
    /** Existing seeds leave upgradeable=false even for basic cards. Match Card.upgrade's actual effects. */
    private static boolean canUpgrade(Card c) {
        return !c.isUpgraded() && (c.getDamage() > 0 || c.getBlock() > 0 || c.getHealAmount() > 0
            || c.getStrengthBonus() > 0 || c.getDexterityBonus() > 0 || c.getVulnerableTurns() > 0
            || c.getWeakTurns() > 0 || c.getPoisonAmount() > 0 || (c.getCostReduction() > 0 && c.getCost() > 0));
    }
    private static String fingerprint(Card c) {
        return c.getName() + ":" + c.getRarity() + ":" + c.isUpgraded() + ":" + c.getDamage() + ":" + c.getBlock() + ":" + c.getCost()
            + ":" + c.getHealAmount() + ":" + c.getStrengthBonus() + ":" + c.getDexterityBonus() + ":" + c.getVulnerableTurns()
            + ":" + c.getWeakTurns() + ":" + c.getPoisonAmount() + ":" + c.getDrawCards() + ":" + c.isExhaust();
    }
    private static String invalid(EventState.Outcome o, EventActor actor) {
        var player = actor.character();
        if (o.unavailableReason != null) return o.unavailableReason;
        if (player.getHp() <= o.hpCost) return "生命不足，支付后必须至少剩1点";
        if (player.getGold() < o.goldCost) return "金币不足";
        if (o.cardIndex >= 0 && (o.cardIndex >= player.getDeck().size()
                || !Objects.equals(o.cardFingerprint, fingerprint(player.getDeck().get(o.cardIndex))))) return "锁定的卡牌目标已变化";
        if (o.relic != null && player.getRelics().stream().anyMatch(r -> Objects.equals(r.getName(), o.relic.getName()))) return "已拥有锁定的遗物";
        return null;
    }
    public static EventPreview preview(EventState state, List<EventActor> actors) {
        var definition = EventCatalog.get(state.definitionId);
        List<EventPreview.Option> options = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            String reason = state.resolved ? "事件已结束" : null;
            Map<String, EventPreview.MemberOutcome> members = new LinkedHashMap<>();
            if (actors.isEmpty()) reason = "没有存活成员";
            for (var actor : actors) {
                var locked = state.members.get(actor.userId());
                if (locked == null) { reason = "成员已变化，请离开事件"; continue; }
                var o = locked.get(index);
                String invalid = invalid(o, actor);
                if (invalid != null) reason = actor.userId() + ": " + invalid;
                members.put(actor.userId(), new EventPreview.MemberOutcome(o.hpCost,o.goldCost,o.reward.name(),o.amount,o.goldReward,o.targetName));
            }
            var option = definition.options().get(index);
            options.add(new EventPreview.Option(option.id(), option.label(), reason == null, reason, members));
        }
        options.add(new EventPreview.Option("leave", "离开", true, null, Map.of()));
        return new EventPreview(state.eventInstanceId,state.definitionId,definition.title(),definition.text(),state.resolved,state.selectedOption,options);
    }
    /** All checks precede every mutation; the caller owns the aggregate lock and one save. */
    public boolean resolve(EventState state, String optionId, List<EventActor> actors) {
        if (state.resolved) return false;
        if (!"leave".equals(optionId)) {
            int index = "option1".equals(optionId) ? 0 : "option2".equals(optionId) ? 1 : -1;
            if (index < 0) throw new InvalidActionException("无效的事件选项");
            var option = preview(state, actors).options().get(index);
            if (!option.enabled()) throw new InvalidActionException(option.disabledReason());
            for (var actor : actors) {
                var o = state.members.get(actor.userId()).get(index);
                var p = actor.character();
                p.setHp(p.getHp() - o.hpCost); p.setGold(p.getGold() - o.goldCost + o.goldReward);
                switch (o.reward) {
                    case GOLD -> p.setGold(p.getGold() + o.amount);
                    case HEAL -> p.heal(o.amount);
                    case HEAL_TO -> p.setHp(Math.max(p.getHp(), Math.min(p.getMaxHp(), o.amount)));
                    case UPGRADE -> p.getDeck().get(o.cardIndex).upgrade();
                    case REMOVE_BASIC, REMOVE_NON_BASIC -> p.getDeck().remove(o.cardIndex);
                    case CARD -> p.getDeck().add(o.card.copy());
                    case RELIC -> p.getRelics().add(o.relic);
                    case NEXT_BATTLE_BLOCK -> actor.blockReward().accept(o.amount);
                }
            }
            actors.forEach(actor -> actor.commit().run());
        }
        state.resolved = true; state.selectedOption = optionId;
        return true;
    }
}
