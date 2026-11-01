package com.dmu.dshorts.domain.topic.repository;

import com.dmu.dshorts.domain.topic.entity.Topic;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
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

    /** Rows {@code [postId (Long), Topic]}: non-deleted topics of many posts, ordered by sortOrder, id. */
    @Query("select p.id, t from Post p join p.topics t where p.id in :postIds and t.deletedAt is null"
            + " order by t.sortOrder, t.id")
    List<Object[]> findPublicTopicsOfPosts(@Param("postIds") Collection<Long> postIds);

    /** Rows {@code [personaId (Long), Topic]}: non-deleted topics of many personas, ordered by sortOrder, id. */
    @Query("select pe.id, t from Persona pe join pe.topics t where pe.id in :personaIds and t.deletedAt is null"
            + " order by t.sortOrder, t.id")
    List<Object[]> findPublicTopicsOfPersonas(@Param("personaIds") Collection<Long> personaIds);

    /** Topic search (first page): name contains pattern (see {@code LikePatterns}), id desc. */
    @Query("select t from Topic t where t.deletedAt is null and lower(t.name) like :pattern escape '!'"
            + " order by t.id desc")
    List<Topic> searchPublic(@Param("pattern") String pattern, Pageable page);

    /** Topic search (next page after id {@code beforeId}). */
    @Query("select t from Topic t where t.deletedAt is null and lower(t.name) like :pattern escape '!'"
            + " and t.id < :beforeId order by t.id desc")
    List<Topic> searchPublicBefore(@Param("pattern") String pattern, @Param("beforeId") Long beforeId,
                                   Pageable page);
}
