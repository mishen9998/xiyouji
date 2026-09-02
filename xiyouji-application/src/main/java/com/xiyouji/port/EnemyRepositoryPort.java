package com.xiyouji.port;

import com.xiyouji.model.Enemy;

import java.util.List;
import java.util.Optional;

/** Application-facing enemy catalog port. */
public interface EnemyRepositoryPort {
    Optional<Enemy> findById(Long id);
    List<Enemy> findAll();
    List<Enemy> findByIsBoss(boolean isBoss);
    List<Enemy> findByLevel(int level);
    List<Enemy> findByIsBossAndLevel(boolean isBoss, int level);
    List<Enemy> findByName(String name);
    Enemy save(Enemy enemy);
}
