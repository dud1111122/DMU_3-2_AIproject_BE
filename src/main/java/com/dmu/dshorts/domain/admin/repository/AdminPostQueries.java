package com.dmu.dshorts.domain.admin.repository;

import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.entity.PostStatus;
import com.dmu.dshorts.global.common.Cursors.TimeIdCursor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import org.springframework.stereotype.Repository;

/** Admin post list: no visibility filter, newest first by {@code (publishedAt, id)}. */
@Repository
public class AdminPostQueries {

    private final EntityManager entityManager;

    public AdminPostQueries(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<Post> findPage(PostStatus status, boolean includeDeleted, TimeIdCursor cursor, int maxRows) {
        StringBuilder jpql = new StringBuilder("select p from Post p join fetch p.persona pe where 1 = 1");
        if (status != null) {
            jpql.append(" and p.status = :status");
        }
        if (!includeDeleted) {
            jpql.append(" and p.deletedAt is null");
        }
        if (cursor != null) {
            jpql.append(" and (p.publishedAt < :time or (p.publishedAt = :time and p.id < :id))");
        }
        jpql.append(" order by p.publishedAt desc, p.id desc");
        TypedQuery<Post> query = entityManager.createQuery(jpql.toString(), Post.class);
        if (status != null) {
            query.setParameter("status", status);
        }
        if (cursor != null) {
            query.setParameter("time", cursor.time());
            query.setParameter("id", cursor.id());
        }
        return query.setMaxResults(maxRows).getResultList();
    }
}
