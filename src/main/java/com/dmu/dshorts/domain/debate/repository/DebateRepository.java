package com.dmu.dshorts.domain.debate.repository;

import com.dmu.dshorts.domain.debate.entity.Debate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DebateRepository extends JpaRepository<Debate, Long> {
}
