package com.dmu.dshorts.domain.post.repository;

import com.dmu.dshorts.domain.post.entity.Post;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * JPQL condition for a publicly visible post: PUBLISHED, not deleted, persona active and not deleted.
     * Requires the aliases {@code p} (Post) and {@code pe} (its persona, e.g. {@code join p.persona pe}).
     * Compile-time constant, so it can be concatenated into {@code @Query} strings in any package:
     * <pre>
     * &#64;Query("select p from Post p join fetch p.persona pe where " + PostRepository.VISIBLE_POST
     *         + " order by p.publishedAt desc, p.id desc")
     * </pre>
     */
    String VISIBLE_POST = "p.status = com.dmu.dshorts.domain.post.entity.PostStatus.PUBLISHED"
            + " and p.deletedAt is null and pe.active = true and pe.deletedAt is null";

    /** Visible post with its persona fetched. Prefer {@code VisibilityService.getVisiblePost(id)}. */
    @Query("select p from Post p join fetch p.persona pe where p.id = :id and " + VISIBLE_POST)
    Optional<Post> findVisibleById(@Param("id") Long id);

    /** Subset of the given IDs that are currently visible. */
    @Query("select p.id from Post p join p.persona pe where p.id in :ids and " + VISIBLE_POST)
    List<Long> findVisibleIds(@Param("ids") Collection<Long> ids);

    /** Any post (hidden/deleted included) with its persona fetched. For admin use only. */
    @Query("select p from Post p join fetch p.persona pe where p.id = :id")
    Optional<Post> findWithPersonaById(@Param("id") Long id);
}
