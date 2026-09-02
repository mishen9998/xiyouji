package com.xiyouji.repository;

import com.xiyouji.model.Relic;
import com.xiyouji.port.RelicRepositoryPort;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data adapter for the application relic catalog port. */
public interface RelicJpaRepository extends JpaRepository<Relic, Long>, RelicRepositoryPort {
}
