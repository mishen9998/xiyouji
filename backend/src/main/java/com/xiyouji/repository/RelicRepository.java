package com.xiyouji.repository;

import com.xiyouji.model.Relic;
import com.xiyouji.model.enums.RelicTier;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RelicRepository extends JpaRepository<Relic, Long> {
    List<Relic> findByTier(RelicTier tier);

    /** 按名称查询宝物（替代 findAll() 全表遍历查找） */
    Optional<Relic> findByName(String name);
}
