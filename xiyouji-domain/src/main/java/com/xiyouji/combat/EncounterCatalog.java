package com.xiyouji.combat;

import com.xiyouji.model.Enemy;
import java.util.*;

/** Explicit three-chapter roster, independent of repository order and unrelated database rows. */
public final class EncounterCatalog {
    public static final int FOCUS_PERCENT = 70;
    public enum Pool { FOCUS, COMPATIBILITY, BOSS }
    public record Chapter(int layer, List<String> focus, List<String> compatibility, String boss) {}
    public record Selection(Enemy template, Pool pool) {}
    private static final Map<Integer, Chapter> CHAPTERS = buildChapters();
    private final List<Enemy> templates;

    public EncounterCatalog(List<Enemy> templates) { this.templates = List.copyOf(templates); }

    private static Map<Integer, Chapter> buildChapters() {
        Map<Integer, List<String>> focus = Map.of(
                1, List.of("寅将军", "熊山君", "白衣秀士"),
                2, List.of("红孩儿", "黄风怪", "如意真仙"),
                3, List.of("黄狮精", "七狮", "铁背苍狼怪"));
        Map<Integer, String> bosses = Map.of(1, "黑熊精", 2, "牛魔王", 3, "大鹏");
        Map<Integer, Chapter> chapters = new LinkedHashMap<>();
        for (int layer = 1; layer <= 3; layer++) {
            int maxLevel = layer;
            List<String> primary = focus.get(layer);
            List<String> compatibility = EnemyContentCatalog.entries().stream()
                    .filter(e -> !e.boss() && e.level() <= maxLevel && !primary.contains(e.name()))
                    .map(EnemyContentCatalog.Entry::name).toList();
            chapters.put(layer, new Chapter(layer, primary, compatibility, bosses.get(layer)));
        }
        return Collections.unmodifiableMap(chapters);
    }

    public static Chapter chapter(int layer) {
        Chapter chapter = CHAPTERS.get(layer);
        if (chapter == null) throw new IllegalArgumentException("Unsupported encounter layer: " + layer);
        return chapter;
    }

    public List<Enemy> pool(int layer, Pool pool) {
        Chapter chapter = chapter(layer);
        List<String> names = switch (pool) {
            case FOCUS -> chapter.focus();
            case COMPATIBILITY -> chapter.compatibility();
            case BOSS -> List.of(chapter.boss());
        };
        return templates.stream().filter(e -> names.contains(e.getName()))
                .filter(e -> e.isBoss() == (pool == Pool.BOSS) && e.getLevel() <= layer)
                .sorted(Comparator.comparing(Enemy::getName)
                        .thenComparing(Enemy::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();
    }

    public Selection select(int layer, boolean boss, long seed) {
        Random random = new Random(seed);
        Pool selected = boss ? Pool.BOSS : (random.nextInt(100) < FOCUS_PERCENT ? Pool.FOCUS : Pool.COMPATIBILITY);
        List<Enemy> eligible = pool(layer, selected);
        if (eligible.isEmpty()) throw new IllegalStateException("Encounter pool is empty: layer=" + layer + ", pool=" + selected);
        Enemy enemy = eligible.get(random.nextInt(eligible.size()));
        if (enemy.getId() == null) throw new IllegalStateException("Encounter enemy has no persisted ID: " + enemy.getName());
        return new Selection(enemy, selected);
    }
}
