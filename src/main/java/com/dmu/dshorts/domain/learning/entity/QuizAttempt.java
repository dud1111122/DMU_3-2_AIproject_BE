package com.dmu.dshorts.domain.learning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Table {@code quiz_attempts}. One member quiz submission (guests are never stored).
 */
@Entity
@Table(name = "quiz_attempts")
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "correct_count", nullable = false)
    private short correctCount;

    @Column(name = "total_count", nullable = false)
    private short totalCount;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    protected QuizAttempt() {
    }

    public QuizAttempt(Long userId, Long postId, short correctCount, short totalCount) {
        this.userId = userId;
        this.postId = postId;
        this.correctCount = correctCount;
        this.totalCount = totalCount;
    }

    @PrePersist
    protected void onPersist() {
        Instant now = Instant.now();
        if (submittedAt == null) {
            submittedAt = now;
        }
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

    public short getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(short correctCount) {
        this.correctCount = correctCount;
    }

    public short getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(short totalCount) {
        this.totalCount = totalCount;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }
}
