package com.xiyouji.service.battle;

import com.xiyouji.combat.CombatRules;
import com.xiyouji.combat.LockedEnemyAction;
import com.xiyouji.combat.EnemyCombat;
import com.xiyouji.model.Enemy;
import com.xiyouji.model.GameCharacter;
import java.util.Map;

/** Additive forecast contract; the existing intent/intentValue/buffs shapes stay unchanged. */
public final class EnemyForecastInfo {
    private EnemyForecastInfo() {}

    public static String rulesVersion(Enemy enemy) {
        return enemy.getRulesVersion() == null ? CombatRules.LEGACY_VERSION : enemy.getRulesVersion();
    }

    public static void append(Map<String, Object> enemyInfo, Enemy enemy, Map<String, GameCharacter> players) {
        LockedEnemyAction action = EnemyCombat.currentForecast(enemy);
        if (action == null) return;
        enemyInfo.put("intentTargetUserIds", action.targetUserIds());
        enemyInfo.put("intentHits", action.hits());
        enemyInfo.put("intentEffects", Map.of("actionType", action.actionType().name(),
                "block", action.block(), "strength", action.strengthGain(), "statuses", action.statusEffects(),
                "targetDamage", EnemyCombat.targetDamageForecast(enemy, players)));
    }
}
