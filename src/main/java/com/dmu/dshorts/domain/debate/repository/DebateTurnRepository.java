package com.dmu.dshorts.domain.debate.repository;

import com.dmu.dshorts.domain.debate.entity.DebateTurn;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DebateTurnRepository extends JpaRepository<DebateTurn, Long> {

    List<DebateTurn> findByDebateIdOrderByTurnOrderAsc(Long debateId);
}
