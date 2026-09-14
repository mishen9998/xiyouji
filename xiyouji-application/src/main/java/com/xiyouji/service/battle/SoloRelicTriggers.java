package com.xiyouji.service.battle;

import com.xiyouji.model.GameCharacter;
import com.xiyouji.model.Relic;
import org.springframework.stereotype.Component;

/**
 * 单人战斗遗物触发解析
 *
 * 解析遗物 effect 字段声明的回合类效果（TURN_START;HEAL:N / DRAW:N），
 * 供战斗开场（首回合）与每轮结束回合共享，消除两处重复解析。
 */
@Component
public class SoloRelicTriggers {

    /** 效果回调：kind 为 HEAL / DRAW，desc 为"回复N点生命"、"抽N张牌"等描述文字 */
    @FunctionalInterface
    public interface EffectListener {
        void onEffect(Relic relic, String kind, String desc);
    }

    /**
     * 应用所有 TURN_START 效果，每触发一项回调一次（由调用方决定写入日志还是战斗记录）。
     *
     * @param player   玩家角色
     * @param listener 效果回调
     */
    public void applyTurnStartEffects(GameCharacter player, EffectListener listener) {
        for (Relic relic : player.getRelics()) {
            if (relic.getEffect() != null && relic.getEffect().contains("TURN_START")) {
                String[] parts = relic.getEffect().split(";");
                for (String p : parts) {
                    if (p.startsWith("HEAL:")) {
                        int healAmount = Integer.parseInt(p.substring(5));
                        player.heal(healAmount);
                        listener.onEffect(relic, "HEAL", "回复" + healAmount + "点生命");
                    } else if (p.startsWith("DRAW:")) {
                        int drawCount = Integer.parseInt(p.substring(5));
                        player.drawCards(drawCount);
                        listener.onEffect(relic, "DRAW", "抽" + drawCount + "张牌");
                    }
                }
            }
        }
    }
}