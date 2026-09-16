package com.xiyouji.service.battle;

import com.xiyouji.combat.EnemyCombat;
import com.xiyouji.exception.BusinessException;
import com.xiyouji.exception.EnemyActionDataException;
import com.xiyouji.model.Enemy;
import java.util.function.Supplier;

/** Content corruption is actionable even when Hibernate wraps a converter failure. */
public final class EnemyBehaviorGuard {
    private EnemyBehaviorGuard() {}
    public static <T> T read(Supplier<T> read) {
        try { return read.get(); }
        catch (RuntimeException e) {
            for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                if (cause instanceof EnemyActionDataException) {
                    throw new BusinessException("ENEMY_BEHAVIOR_INVALID", cause.getMessage(), 500, e);
                }
            }
            throw e;
        }
    }
    public static void validate(Enemy enemy) {
        read(() -> { EnemyCombat.validate(enemy); return null; });
    }
}
