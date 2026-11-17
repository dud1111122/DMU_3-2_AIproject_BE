package com.dmu.dshorts.domain.debate.repository;

import com.dmu.dshorts.domain.debate.entity.Debate;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DebateRepository extends JpaRepository<Debate, Long> {

    /** Both participants visible (active, not deleted). Requires the alias {@code d}. */
    String BOTH_PERSONAS_VISIBLE =
            "d.personaAId in (select pe.id from Persona pe where " + PersonaRepository.VISIBLE_PERSONA + ")"
            + " and d.personaBId in (select pe.id from Persona pe where " + PersonaRepository.VISIBLE_PERSONA + ")";

    Optional<Debate> findByIdAndUserId(Long id, Long userId);

    /** All own debates created in {@code [from, to)} regardless of visibility (daily quota). */
    long countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long userId, Instant from, Instant to);

    @Query("select d from Debate d where d.userId = :userId and " + BOTH_PERSONAS_VISIBLE
            + " order by d.createdAt desc, d.id desc")
    List<Debate> findVisibleFirstPage(@Param("userId") Long userId, Pageable page);

    @Query("select d from Debate d where d.userId = :userId and " + BOTH_PERSONAS_VISIBLE
            + " and (d.createdAt < :time or (d.createdAt = :time and d.id < :id))"
            + " order by d.createdAt desc, d.id desc")
    List<Debate> findVisibleAfter(@Param("userId") Long userId, @Param("time") Instant time,
                                  @Param("id") Long id, Pageable page);
}
