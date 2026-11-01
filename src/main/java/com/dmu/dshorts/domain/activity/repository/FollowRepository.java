package com.dmu.dshorts.domain.activity.repository;

import com.dmu.dshorts.domain.activity.entity.Follow;
import com.dmu.dshorts.domain.activity.entity.FollowId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FollowRepository extends JpaRepository<Follow, FollowId> {

    /** Idempotent follow: inserts the row unless it already exists. */
    @Modifying
    @Query(value = "insert into follows (user_id, persona_id) values (:userId, :personaId) on conflict do nothing",
            nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("personaId") Long personaId);

    @Modifying
    @Query("delete from Follow f where f.userId = :userId and f.personaId = :personaId")
    int deleteOne(@Param("userId") Long userId, @Param("personaId") Long personaId);

    /** The subset of {@code personaIds} that the user follows. */
    @Query("select f.personaId from Follow f where f.userId = :userId and f.personaId in :personaIds")
    List<Long> findFollowedPersonaIds(@Param("userId") Long userId, @Param("personaIds") Collection<Long> personaIds);

    long countByPersonaId(Long personaId);
}
