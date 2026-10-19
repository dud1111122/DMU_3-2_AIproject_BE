package com.dmu.dshorts.domain.debate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Table {@code debates}. Successful member debate. personaLowId < personaHighId (sorted pair). Unique per (user, pair, topicKey).
 */
@Entity
@Table(name = "debates")
public class Debate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "persona_low_id", nullable = false)
    private Long personaLowId;

    @Column(name = "persona_high_id", nullable = false)
    private Long personaHighId;

    @Column(name = "topic_text", nullable = false, length = 200)
    private String topicText;

    @Column(name = "topic_key", nullable = false, length = 200)
    private String topicKey;

    @Column(name = "issue_summary", nullable = false)
    private String issueSummary;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    protected Debate() {
    }

    public Debate(Long userId, Long personaLowId, Long personaHighId, String topicText, String topicKey, String issueSummary) {
        this.userId = userId;
        this.personaLowId = personaLowId;
        this.personaHighId = personaHighId;
        this.topicText = topicText;
        this.topicKey = topicKey;
        this.issueSummary = issueSummary;
    }

    @PrePersist
    protected void onPersist() {
        Instant now = Instant.now();
        if (completedAt == null) {
            completedAt = now;
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

    public Long getPersonaLowId() {
        return personaLowId;
    }

    public void setPersonaLowId(Long personaLowId) {
        this.personaLowId = personaLowId;
    }

    public Long getPersonaHighId() {
        return personaHighId;
    }

    public void setPersonaHighId(Long personaHighId) {
        this.personaHighId = personaHighId;
    }

    public String getTopicText() {
        return topicText;
    }

    public void setTopicText(String topicText) {
        this.topicText = topicText;
    }

    public String getTopicKey() {
        return topicKey;
    }

    public void setTopicKey(String topicKey) {
        this.topicKey = topicKey;
    }

    public String getIssueSummary() {
        return issueSummary;
    }

    public void setIssueSummary(String issueSummary) {
        this.issueSummary = issueSummary;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
