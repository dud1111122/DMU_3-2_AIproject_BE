package com.dmu.dshorts.domain.activity.repository;

import com.dmu.dshorts.domain.activity.entity.PostLike;
import com.dmu.dshorts.domain.activity.entity.PostLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {

    /** Idempotent like: inserts the row unless it already exists. */
    @Modifying
    @Query(value = "insert into post_likes (user_id, post_id) values (:userId, :postId) on conflict do nothing",
            nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("postId") Long postId);

    @Modifying
    @Query("delete from PostLike l where l.userId = :userId and l.postId = :postId")
    int deleteOne(@Param("userId") Long userId, @Param("postId") Long postId);

    /** Rows {@code [postId (Long), count (Long)]}; posts without likes are absent. */
    @Query("select l.postId, count(l) from PostLike l where l.postId in :postIds group by l.postId")
    List<Object[]> countByPostIds(@Param("postIds") Collection<Long> postIds);

    /** The subset of {@code postIds} that the user liked. */
    @Query("select l.postId from PostLike l where l.userId = :userId and l.postId in :postIds")
    List<Long> findLikedPostIds(@Param("userId") Long userId, @Param("postIds") Collection<Long> postIds);
}
