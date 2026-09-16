package com.xiyouji.combat;

/** Stable content vocabulary. Unknown values must fail validation, never become ATTACK. */
public enum EnemyActionType {
    ATTACK, ATTACK_ALL, MULTI_HIT, DEFEND, ATTACK_DEFEND, GAIN_STRENGTH, APPLY_STATUS
}
