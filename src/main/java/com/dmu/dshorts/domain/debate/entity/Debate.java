package com.dmu.dshorts.domain.debate.entity;

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
 * Table {@code debates}. A live conversation between two personas (A = first requested, speaks first).
 * {@code turnCount} counts AI and user turns; 12 turns end the debate.
 */
@Entity
@Table(name = "debates")
public class Debate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "persona_a_id", nullable = false)
    private Long personaAId;

    @Column(name = "persona_b_id", nullable = false)
    private Long personaBId;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DebateStatus status;

    @Column(name = "turn_count", nullable = false)
    private Short turnCount;

    @Column(name = "summary")
    private String summary;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Debate() {
    }

    public Debate(Long userId, Long personaAId, Long personaBId, String topic) {
        this.userId = userId;
        this.personaAId = personaAId;
        this.personaBId = personaBId;
        this.topic = topic;
        this.status = DebateStatus.ACTIVE;
        this.turnCount = 0;
    }

    @PrePersist
    protected void onPersist() {
        Instant now = Instant.now();
        if (status == null) {
            status = DebateStatus.ACTIVE;
        }
        if (turnCount == null) {
            turnCount = 0;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
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

    public Long getPersonaAId() {
        return personaAId;
    }

    public Long getPersonaBId() {
        return personaBId;
    }

    public String getTopic() {
        return topic;
    }

    public DebateStatus getStatus() {
        return status;
    }

    public void setStatus(DebateStatus status) {
        this.status = status;
    }

    public int getTurnCount() {
        return turnCount == null ? 0 : turnCount;
    }

    public void setTurnCount(int turnCount) {
        this.turnCount = (short) turnCount;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
