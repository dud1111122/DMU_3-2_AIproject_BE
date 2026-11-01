package com.dmu.dshorts.domain.post.repository;

import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.global.common.Cursors.TimeIdCursor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keyset list queries over publicly visible posts with optional filters. Every query applies
 * {@link PostRepository#VISIBLE_POST} and fetches the persona.
 */
@Repository
public class PostQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Optional filters of a newest-first post list.
     *
     * @param topicId        only posts linked to this (non-deleted) topic
     * @param followerUserId only posts of personas this user follows
     * @param personaId      only posts of this persona
     */
    public record Filter(Long topicId, Long followerUserId, Long personaId) {

        public static Filter none() {
            return new Filter(null, null, null);
        }
    }

    /** Visible posts, {@code publishedAt desc, id desc}, after the cursor, at most {@code maxResults}. */
    public List<Post> findVisible(Filter filter, TimeIdCursor cursor, int maxResults) {
        StringBuilder jpql = new StringBuilder("select p from Post p join fetch p.persona pe where ")
                .append(PostRepository.VISIBLE_POST);
        Map<String, Object> params = new HashMap<>();
        if (filter.topicId() != null) {
            jpql.append(" and exists (select 1 from Post p2 join p2.topics t"
                    + " where p2.id = p.id and t.id = :topicId and t.deletedAt is null)");
            params.put("topicId", filter.topicId());
        }
        if (filter.followerUserId() != null) {
            jpql.append(" and exists (select 1 from Follow f where f.userId = :followerUserId and f.personaId = pe.id)");
            params.put("followerUserId", filter.followerUserId());
        }
        if (filter.personaId() != null) {
            jpql.append(" and pe.id = :personaId");
            params.put("personaId", filter.personaId());
        }
        if (cursor != null) {
            jpql.append(" and (p.publishedAt < :cursorTime or (p.publishedAt = :cursorTime and p.id < :cursorId))");
            params.put("cursorTime", cursor.time());
            params.put("cursorId", cursor.id());
        }
        jpql.append(" order by p.publishedAt desc, p.id desc");
        return run(jpql.toString(), params, maxResults);
    }

    /**
     * Visible posts whose title or any card body contains the text, {@code id desc}, with id below {@code beforeId}.
     *
     * @param pattern from {@link LikePatterns#contains(String)}
     */
    public List<Post> searchVisible(String pattern, Long beforeId, int maxResults) {
        StringBuilder jpql = new StringBuilder("select p from Post p join fetch p.persona pe where ")
                .append(PostRepository.VISIBLE_POST)
                .append(" and (lower(p.title) like :pattern escape '!'"
                        + " or exists (select 1 from PostSection s where s.postId = p.id"
                        + " and lower(s.body) like :pattern escape '!'))");
        Map<String, Object> params = new HashMap<>();
        params.put("pattern", pattern);
        if (beforeId != null) {
            jpql.append(" and p.id < :beforeId");
            params.put("beforeId", beforeId);
        }
        jpql.append(" order by p.id desc");
        return run(jpql.toString(), params, maxResults);
    }

    private List<Post> run(String jpql, Map<String, Object> params, int maxResults) {
        TypedQuery<Post> query = entityManager.createQuery(jpql, Post.class);
        params.forEach(query::setParameter);
        query.setMaxResults(maxResults);
        return query.getResultList();
    }
}
