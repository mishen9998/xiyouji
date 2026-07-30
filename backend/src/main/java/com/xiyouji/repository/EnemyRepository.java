package com.xiyouji.repository;

import com.xiyouji.model.Enemy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EnemyRepository extends JpaRepository<Enemy, Long> {
    List<Enemy> findByIsBoss(boolean isBoss);
    List<Enemy> findByLevel(int level);
    List<Enemy> findByIsBossAndLevel(boolean isBoss, int level);
    List<Enemy> findByName(String name);
}
