package com.dmu.dshorts.domain.study.repository;

import com.dmu.dshorts.domain.learning.entity.ReviewItem;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Study-chat side writes to {@code review_items} (owned by the learning package). Same semantics as the quiz
 * side and DB_설계 "복습 재개": one row per (user, key point); a new miss reopens it as OPEN with
 * {@code completed_at = null}. {@code post_id} is never changed (the FK keeps it consistent with the key point).
 */
public interface StudyReviewItemCommands extends Repository<ReviewItem, Long> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO review_items(user_id, post_id, key_point_id, reason)
            VALUES (:userId, :postId, :keyPointId, :reason)
            ON CONFLICT (user_id, key_point_id)
            DO UPDATE SET reason = EXCLUDED.reason,
                          status = 'OPEN', completed_at = NULL, updated_at = now()
            """, nativeQuery = true)
    int upsertOpen(@Param("userId") Long userId, @Param("postId") Long postId,
                   @Param("keyPointId") Long keyPointId, @Param("reason") String reason);
}
