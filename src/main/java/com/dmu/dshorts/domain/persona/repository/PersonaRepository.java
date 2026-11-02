package com.dmu.dshorts.domain.persona.repository;

import com.dmu.dshorts.domain.persona.entity.Persona;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonaRepository extends JpaRepository<Persona, Long> {

    /**
     * JPQL condition for a publicly visible persona (active and not deleted). Requires the alias {@code pe}.
     * Usable inside {@code @Query} strings like {@link com.dmu.dshorts.domain.post.repository.PostRepository#VISIBLE_POST}.
     */
    String VISIBLE_PERSONA = "pe.active = true and pe.deletedAt is null";

    Optional<Persona> findByUsername(String username);

    /** Prefer {@code VisibilityService.getVisiblePersona(id)}. */
    @Query("select pe from Persona pe where pe.id = :id and " + VISIBLE_PERSONA)
    Optional<Persona> findVisibleById(@Param("id") Long id);

    @Query("select pe.id from Persona pe where pe.id in :ids and " + VISIBLE_PERSONA)
    List<Long> findVisibleIds(@Param("ids") Collection<Long> ids);

    /**
     * Recommendation ranking: visible personas with the number of their topics among {@code topicIds}
     * (pass only non-deleted topic IDs), ordered by that count desc, id asc. Rows {@code [id, overlapCount]}.
     */
    @Query(value = "select pe.id, count(pt.topic_id) as overlap from personas pe"
            + " left join persona_topics pt on pt.persona_id = pe.id and pt.topic_id in (:topicIds)"
            + " where pe.active = true and pe.deleted_at is null"
            + " group by pe.id order by overlap desc, pe.id asc limit :maxResults", nativeQuery = true)
    List<Object[]> rankByTopicOverlap(@Param("topicIds") Collection<Long> topicIds,
                                      @Param("maxResults") int maxResults);
}
