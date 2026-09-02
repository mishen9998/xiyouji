package com.xiyouji.port;

import com.xiyouji.model.Relic;

import java.util.List;

/** Application-facing relic catalog port. */
public interface RelicRepositoryPort {
    List<Relic> findAll();
    Relic save(Relic relic);
}
