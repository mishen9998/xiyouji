package com.xiyouji.repository;

import com.xiyouji.model.Enemy;
import com.xiyouji.port.EnemyRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data adapter for the application enemy catalog port. */
public interface EnemyJpaRepository extends JpaRepository<Enemy, Long>, EnemyRepositoryPort {
    List<Enemy> findByIsBoss(boolean isBoss);
    List<Enemy> findByLevel(int level);
    List<Enemy> findByIsBossAndLevel(boolean isBoss, int level);
    List<Enemy> findByName(String name);
}
