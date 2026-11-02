package com.dmu.dshorts.domain.activity.repository;

import com.dmu.dshorts.domain.activity.entity.SavedPost;
import com.dmu.dshorts.domain.activity.entity.SavedPostId;
import com.dmu.dshorts.domain.post.repository.PostRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface SavedPostRepository extends JpaRepository<SavedPost, SavedPostId> {

    String VISIBLE_SAVED = "select s from SavedPost s join Post p on p.id = s.postId join p.persona pe"
            + " where s.userId = :userId and " + PostRepository.VISIBLE_POST;

    /** Idempotent save: inserts the row unless it already exists. */
    @Modifying
    @Query(value = "insert into saved_posts (user_id, post_id) values (:userId, :postId) on conflict do nothing",
            nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("postId") Long postId);

    @Modifying
    @Query("delete from SavedPost s where s.userId = :userId and s.postId = :postId")
    int deleteOne(@Param("userId") Long userId, @Param("postId") Long postId);

    /** The subset of {@code postIds} that the user saved. */
    @Query("select s.postId from SavedPost s where s.userId = :userId and s.postId in :postIds")
    List<Long> findSavedPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);

    /** Saved rows of currently visible posts, newest save first (first page). */
    @Query(VISIBLE_SAVED + " order by s.createdAt desc, s.postId desc")
    List<SavedPost> findVisiblePage(@Param("userId") Long userId, Pageable page);

    /** Saved rows of currently visible posts after the cursor {@code (createdAt, postId)}. */
    @Query(VISIBLE_SAVED + " and (s.createdAt < :time or (s.createdAt = :time and s.postId < :postId))"
            + " order by s.createdAt desc, s.postId desc")
    List<SavedPost> findVisiblePageAfter(@Param("userId") Long userId, @Param("time") Instant time,
                                         @Param("postId") Long postId, Pageable page);
}
