package com.dmu.dshorts.domain.debate.repository;

import com.dmu.dshorts.domain.debate.entity.DebateTurn;
import com.dmu.dshorts.domain.debate.entity.DebateTurnId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DebateTurnRepository extends JpaRepository<DebateTurn, DebateTurnId> {

    List<DebateTurn> findByDebateIdOrderByTurnOrderAsc(Long debateId);
}
