package com.dmu.dshorts.domain.learning.repository;

import com.dmu.dshorts.domain.learning.entity.ReviewItem;
import com.dmu.dshorts.domain.learning.entity.ReviewReason;
import com.dmu.dshorts.domain.learning.entity.ReviewStatus;
import com.dmu.dshorts.domain.post.repository.PostRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewItemRepository extends JpaRepository<ReviewItem, Long> {

    String REVIEW_ROW_FROM = "select r.id as id, r.postId as postId, p.title as postTitle,"
            + " r.keyPointId as keyPointId, k.pointOrder as keyPointOrder, k.text as keyPointText,"
            + " r.reason as reason, r.status as status, r.updatedAt as updatedAt, r.completedAt as completedAt"
            + " from ReviewItem r join Post p on p.id = r.postId join p.persona pe"
            + " join KeyPoint k on k.id = r.keyPointId";

    String MY_VISIBLE_REVIEWS = REVIEW_ROW_FROM
            + " where r.userId = :userId and r.status in :statuses and " + PostRepository.VISIBLE_POST;

    Optional<ReviewItem> findByUserIdAndKeyPointId(Long userId, Long keyPointId);

    Optional<ReviewItem> findByIdAndUserId(Long id, Long userId);

    List<ReviewItem> findByUserIdAndPostId(Long userId, Long postId);

    @Query(MY_VISIBLE_REVIEWS + " order by r.updatedAt desc, r.id desc")
    List<ReviewRow> findMyVisibleFirstPage(@Param("userId") Long userId,
                                           @Param("statuses") Collection<ReviewStatus> statuses, Pageable page);

    @Query(MY_VISIBLE_REVIEWS
            + " and (r.updatedAt < :time or (r.updatedAt = :time and r.id < :id))"
            + " order by r.updatedAt desc, r.id desc")
    List<ReviewRow> findMyVisibleAfter(@Param("userId") Long userId,
                                       @Param("statuses") Collection<ReviewStatus> statuses,
                                       @Param("time") Instant time, @Param("id") Long id, Pageable page);

    /** One review item of the user, only while its post is visible. */
    @Query(REVIEW_ROW_FROM + " where r.id = :id and r.userId = :userId and " + PostRepository.VISIBLE_POST)
    Optional<ReviewRow> findMyVisibleRow(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Atomic open-or-reopen (DB_설계 §5 "복습 재개"): inserts an OPEN item or, on a (user_id, key_point_id)
     * conflict, sets reason, status OPEN, completed_at NULL and updated_at. {@code now} comes from the application
     * clock, the same clock the entity's {@code @PreUpdate} uses, so timestamps stay ordered. The post_id of an
     * existing row is not changed (the composite FK guarantees it is the key point's post).
     */
    @Modifying(flushAutomatically = true)
    @Query(nativeQuery = true, value = "insert into review_items(user_id, post_id, key_point_id, reason, updated_at)"
            + " values (:userId, :postId, :keyPointId, :reason, :now)"
            + " on conflict (user_id, key_point_id) do update set reason = excluded.reason,"
            + " status = 'OPEN', completed_at = null, updated_at = excluded.updated_at")
    int upsertOpen(@Param("userId") Long userId, @Param("postId") Long postId,
                   @Param("keyPointId") Long keyPointId, @Param("reason") String reason,
                   @Param("now") Instant now);

    /** Projection for review lists and PATCH responses. */
    interface ReviewRow {
        Long getId();

        Long getPostId();

        String getPostTitle();

        Long getKeyPointId();

        Short getKeyPointOrder();

        String getKeyPointText();

        ReviewReason getReason();

        ReviewStatus getStatus();

        Instant getUpdatedAt();

        Instant getCompletedAt();
    }
}
