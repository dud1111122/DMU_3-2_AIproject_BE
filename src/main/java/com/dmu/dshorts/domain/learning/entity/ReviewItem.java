package com.dmu.dshorts.domain.learning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Table {@code review_items}. One review item per (user, key point). DONE ⇔ completedAt set (DB CHECK).
 */
@Entity
@Table(name = "review_items")
public class ReviewItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "key_point_id", nullable = false)
    private Long keyPointId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private ReviewReason reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReviewStatus status = ReviewStatus.OPEN;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ReviewItem() {
    }

    public ReviewItem(Long userId, Long postId, Long keyPointId, ReviewReason reason) {
        this.userId = userId;
        this.postId = postId;
        this.keyPointId = keyPointId;
        this.reason = reason;
    }

    @PrePersist
    protected void onPersist() {
        Instant now = Instant.now();
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public Long getKeyPointId() {
        return keyPointId;
    }

    public void setKeyPointId(Long keyPointId) {
        this.keyPointId = keyPointId;
    }

    public ReviewReason getReason() {
        return reason;
    }

    public void setReason(ReviewReason reason) {
        this.reason = reason;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public void setStatus(ReviewStatus status) {
        this.status = status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
