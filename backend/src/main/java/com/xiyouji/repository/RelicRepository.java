package com.xiyouji.repository;

import com.xiyouji.model.Relic;
import com.xiyouji.model.enums.RelicTier;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RelicRepository extends JpaRepository<Relic, Long> {
    List<Relic> findByTier(RelicTier tier);
}
