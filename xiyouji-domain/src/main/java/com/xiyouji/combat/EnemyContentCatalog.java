package com.xiyouji.combat;

import com.xiyouji.model.Enemy;
import java.util.*;
import static com.xiyouji.combat.EnemyActionDefinition.*;
import static com.xiyouji.combat.EnemyActionDefinition.TargetScope.*;
import static com.xiyouji.model.enums.BuffType.*;

/** Immutable content keys and cycles. Existing IDs, stats, names and artwork are never replaced. */
public final class EnemyContentCatalog {
    public static final int CONTENT_VERSION = 2;
    public record Entry(String key, String name, int level, boolean boss,
                        List<String> legacyMoves, List<EnemyActionDefinition> actions) {}
    private static final Map<String, Entry> ENTRIES = build();
    private EnemyContentCatalog() {}

    private static Map<String, Entry> build() {
        Map<String, Entry> entries = new LinkedHashMap<>();
        add(entries, "小妖", 1, false, List.of("attack", "attack", "defend"), null);
        add(entries, "妖兵", 1, false, List.of("attack", "attack_defend", "attack"), null);
        add(entries, "白骨精", 2, false, List.of("attack", "attack", "buff", "attack"), null);
        add(entries, "蜘蛛精", 2, false, List.of("attack", "defend", "attack"), null);
        add(entries, "红孩儿", 2, false, List.of("attack", "attack", "attack", "buff"), List.of(attack(100), allAttack(80), strength(2), attack(150)));
        add(entries, "黄风怪", 2, false, List.of("attack_defend", "attack", "attack_defend"), List.of(attackDefend(75), status(ALL, WEAK, 2), attack(100)));
        add(entries, "银角大王", 2, false, List.of("attack", "attack", "buff", "attack"), null);
        add(entries, "牛魔王", 1, true, List.of("attack", "attack_defend", "attack", "buff"), List.of(allAttack(80), defend(), strength(2), multiHit(50, 3)));
        add(entries, "铁扇公主", 1, true, List.of("attack", "defend", "attack", "buff"), null);
        add(entries, "金角大王", 1, true, List.of("attack", "attack", "attack_defend", "buff"), null);
        add(entries, "黄袍怪", 1, true, List.of("attack", "attack_defend", "attack"), null);
        add(entries, "灵感大王", 1, true, List.of("attack", "buff", "attack", "attack_defend"), null);
        add(entries, "寅将军", 1, false, List.of("attack", "attack", "defend"), List.of(attack(100), defend(), attack(150)));
        add(entries, "熊山君", 1, false, List.of("attack", "defend", "attack"), List.of(defend(), attack(100), multiHit(45, 3)));
        add(entries, "特处士", 1, false, List.of("defend", "attack", "attack"), null);
        add(entries, "白衣秀士", 1, false, List.of("attack", "attack", "defend"), List.of(attack(100), status(SINGLE, WEAK, 1), attackDefend(75)));
        add(entries, "凌虚子", 1, false, List.of("attack", "attack", "attack_defend"), null);
        add(entries, "精细鬼", 1, false, List.of("attack", "defend", "attack"), null);
        add(entries, "伶俐虫", 1, false, List.of("defend", "attack", "attack"), null);
        add(entries, "巴山虎", 1, false, List.of("attack", "attack", "defend"), null);
        add(entries, "倚海龙", 1, false, List.of("attack", "defend", "attack"), null);
        add(entries, "压龙大仙", 1, false, List.of("buff", "defend", "attack"), null);
        add(entries, "六健将", 1, false, List.of("attack", "attack", "attack_defend"), null);
        add(entries, "奔波儿灞", 1, false, List.of("attack", "defend", "attack"), null);
        add(entries, "灞波儿奔", 1, false, List.of("defend", "attack", "attack"), null);
        add(entries, "小钻风", 1, false, List.of("attack", "attack", "defend"), null);
        add(entries, "刁钻古怪", 1, false, List.of("attack", "defend", "attack"), null);
        add(entries, "古怪刁钻", 1, false, List.of("defend", "attack", "attack"), null);
        add(entries, "有来有去", 1, false, List.of("attack", "attack", "defend"), null);
        add(entries, "斑衣鳜婆", 1, false, List.of("buff", "defend", "attack"), null);
        add(entries, "玉面公主", 1, false, List.of("attack", "defend", "buff"), null);
        add(entries, "白面狐狸", 1, false, List.of("attack", "buff", "defend"), null);
        add(entries, "杏仙", 1, false, List.of("buff", "attack", "defend"), null);
        add(entries, "虫妖干儿子", 1, false, List.of("attack", "attack", "defend"), null);
        add(entries, "虎先锋", 2, false, List.of("attack", "attack", "attack_defend", "defend"), null);
        add(entries, "狐阿七大王", 2, false, List.of("attack", "attack_defend", "attack", "buff"), null);
        add(entries, "如意真仙", 2, false, List.of("attack", "defend", "attack", "buff"), List.of(status(SINGLE, VULNERABLE, 1), defend(), multiHit(45, 3), attack(100)));
        add(entries, "黄狮精", 2, false, List.of("attack", "attack_defend", "attack", "buff"), List.of(attack(100), attackDefend(75), attack(150), strength(1)));
        add(entries, "七狮", 2, false, List.of("attack", "attack", "attack_defend", "buff"), List.of(allAttack(70), multiHit(45, 3), defend(), status(ALL, WEAK, 1)));
        add(entries, "铁背苍狼怪", 2, false, List.of("buff", "attack", "defend", "attack_defend"), List.of(status(SINGLE, WEAK, 1), attackDefend(75), status(SINGLE, VULNERABLE, 1), attack(150)));
        add(entries, "黑熊精", 1, true, List.of("attack", "attack_defend", "attack", "buff"), List.of(attack(100), attackDefend(75), strength(2), attack(150)));
        add(entries, "鼍龙", 1, true, List.of("attack", "attack", "attack_defend"), null);
        add(entries, "虎力大仙", 2, true, List.of("buff", "attack", "defend", "attack"), null);
        add(entries, "鹿力大仙", 2, true, List.of("buff", "defend", "attack", "attack"), null);
        add(entries, "羊力大仙", 2, true, List.of("defend", "buff", "attack", "attack"), null);
        add(entries, "独角兕大王", 3, true, List.of("attack", "attack_defend", "attack", "buff"), null);
        add(entries, "蝎子精", 2, true, List.of("attack", "attack", "attack", "buff"), null);
        add(entries, "六耳猕猴", 3, true, List.of("attack", "attack_defend", "attack", "buff"), null);
        add(entries, "九头虫", 2, true, List.of("attack", "attack", "attack_defend", "buff"), null);
        add(entries, "黄眉大王", 3, true, List.of("attack", "buff", "attack", "attack_defend"), null);
        add(entries, "赛太岁", 2, true, List.of("buff", "attack", "attack_defend", "attack"), null);
        add(entries, "百眼魔君", 2, true, List.of("buff", "attack", "attack", "buff"), null);
        add(entries, "青狮", 3, true, List.of("attack", "attack", "attack_defend", "buff"), null);
        add(entries, "白象", 3, true, List.of("attack", "attack_defend", "attack", "defend"), null);
        add(entries, "大鹏", 3, true, List.of("attack", "attack", "attack", "buff"), List.of(multiHit(50, 3), status(ALL, WEAK, 2), allAttack(100), strength(2)));
        add(entries, "白鹿精", 2, true, List.of("buff", "attack", "defend", "attack"), null);
        add(entries, "金鼻白毛老鼠精", 2, true, List.of("attack", "defend", "attack", "buff"), null);
        add(entries, "南山大王", 1, true, List.of("buff", "attack", "defend", "attack"), null);
        add(entries, "玉兔精", 2, true, List.of("attack", "attack", "buff", "defend"), null);
        add(entries, "辟寒大王", 2, true, List.of("attack", "attack_defend", "attack", "defend"), null);
        add(entries, "辟暑大王", 2, true, List.of("attack_defend", "attack", "attack", "defend"), null);
        add(entries, "辟尘大王", 2, true, List.of("defend", "attack", "attack_defend", "attack"), null);
        add(entries, "九灵元圣", 3, true, List.of("buff", "attack", "attack", "attack_defend"), null);
        return Collections.unmodifiableMap(entries);
    }

    private static void add(Map<String, Entry> entries, String name, int level, boolean boss,
                            List<String> moves, List<EnemyActionDefinition> actions) {
        entries.put(name, new Entry("xiyouji.enemy." + name, name, level, boss, List.copyOf(moves),
                actions == null ? moves.stream().map(EnemyActionDefinition::legacyMove).toList() : actions));
    }

    public static Collection<Entry> entries() { return ENTRIES.values(); }
    public static Entry require(String name) {
        Entry entry = ENTRIES.get(name);
        if (entry == null) throw new IllegalArgumentException("Unknown enemy content: " + name);
        return entry;
    }

    /** Run under the existing seed lock; a repeat startup never overwrites a same/newer version. */
    public static boolean upgrade(Enemy enemy) {
        Entry entry = ENTRIES.get(enemy.getName());
        if (entry == null) return false;
        if (enemy.getContentVersion() >= CONTENT_VERSION) return false;
        if (enemy.getContentKey() != null && !enemy.getContentKey().equals(entry.key())) {
            throw new IllegalStateException("Enemy content key mismatch: id=" + enemy.getId());
        }
        enemy.setContentKey(entry.key());
        enemy.setContentVersion(CONTENT_VERSION);
        enemy.setRulesVersion(CombatRules.VERSION);
        enemy.setActionDefinitions(new ArrayList<>(entry.actions()));
        return true;
    }
}

