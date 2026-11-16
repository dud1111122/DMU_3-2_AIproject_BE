package com.dmu.dshorts.domain.debate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Table {@code debate_turns}. One turn of a debate: a persona turn (AI, speaker persona set) or a user turn
 * (speaker persona null). {@code turnOrder} is 1..12 and unique per debate.
 */
@Entity
@Table(name = "debate_turns")
public class DebateTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "debate_id", nullable = false)
    private Long debateId;

    @Column(name = "turn_order", nullable = false)
    private Short turnOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "speaker_type", nullable = false)
    private SpeakerType speakerType;

    @Column(name = "speaker_persona_id")
    private Long speakerPersonaId;

    @Column(name = "body", nullable = false, length = 300)
    private String body;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    @Column(name = "created_at")
    private Instant createdAt;

    protected DebateTurn() {
    }

    public DebateTurn(Long debateId, int turnOrder, SpeakerType speakerType, Long speakerPersonaId, String body,
                      String aiModel) {
        this.debateId = debateId;
        this.turnOrder = (short) turnOrder;
        this.speakerType = speakerType;
        this.speakerPersonaId = speakerPersonaId;
        this.body = body;
        this.aiModel = aiModel;
    }

    @PrePersist
    protected void onPersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getDebateId() {
        return debateId;
    }

    public int getTurnOrder() {
        return turnOrder;
    }

    public SpeakerType getSpeakerType() {
        return speakerType;
    }

    public Long getSpeakerPersonaId() {
        return speakerPersonaId;
    }

    public String getBody() {
        return body;
    }

    public String getAiModel() {
        return aiModel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
