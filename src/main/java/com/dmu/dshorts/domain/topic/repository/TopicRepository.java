package com.dmu.dshorts.domain.topic.repository;

import com.dmu.dshorts.domain.topic.entity.Topic;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    /** Public topic list: not deleted, ordered by sortOrder, id. */
    List<Topic> findByDeletedAtIsNullOrderBySortOrderAscIdAsc();

    Optional<Topic> findByName(String name);

    Optional<Topic> findByIdAndDeletedAtIsNull(Long id);

    List<Topic> findByIdInAndDeletedAtIsNull(Collection<Long> ids);

    /** Non-deleted topics linked to a post (post_topics), ordered by sortOrder, id. */
    @Query("select t from Post p join p.topics t where p.id = :postId and t.deletedAt is null"
            + " order by t.sortOrder, t.id")
    List<Topic> findPublicTopicsOfPost(@Param("postId") Long postId);

    /** Non-deleted topics linked to a persona (persona_topics), ordered by sortOrder, id. */
    @Query("select t from Persona pe join pe.topics t where pe.id = :personaId and t.deletedAt is null"
            + " order by t.sortOrder, t.id")
    List<Topic> findPublicTopicsOfPersona(@Param("personaId") Long personaId);
}
