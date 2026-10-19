package com.dmu.dshorts.domain.study.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Table {@code learning_assessments}. Latest assessment of a key point in a conversation; evidence must be a USER message of the same room.
 */
@Entity
@Table(name = "learning_assessments")
@IdClass(LearningAssessmentId.class)
public class LearningAssessment {

    @Id
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Id
    @Column(name = "key_point_id", nullable = false)
    private Long keyPointId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "evidence_message_id", nullable = false)
    private Long evidenceMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false)
    private AssessmentLevel level;

    @Column(name = "feedback", nullable = false)
    private String feedback;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LearningAssessment() {
    }

    public LearningAssessment(Long conversationId, Long keyPointId, Long postId, Long evidenceMessageId, AssessmentLevel level, String feedback) {
        this.conversationId = conversationId;
        this.keyPointId = keyPointId;
        this.postId = postId;
        this.evidenceMessageId = evidenceMessageId;
        this.level = level;
        this.feedback = feedback;
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

    public Long getConversationId() {
        return conversationId;
    }

    public Long getKeyPointId() {
        return keyPointId;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public Long getEvidenceMessageId() {
        return evidenceMessageId;
    }

    public void setEvidenceMessageId(Long evidenceMessageId) {
        this.evidenceMessageId = evidenceMessageId;
    }

    public AssessmentLevel getLevel() {
        return level;
    }

    public void setLevel(AssessmentLevel level) {
        this.level = level;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
