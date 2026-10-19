package com.dmu.dshorts.domain.debate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Table {@code debate_turns}. Turn 1..6 of a debate; speaker must be one of the two personas (DB CHECK).
 */
@Entity
@Table(name = "debate_turns")
@IdClass(DebateTurnId.class)
public class DebateTurn {

    @Id
    @Column(name = "debate_id", nullable = false)
    private Long debateId;

    @Id
    @Column(name = "turn_order", nullable = false)
    private Short turnOrder;

    @Column(name = "persona_low_id", nullable = false)
    private Long personaLowId;

    @Column(name = "persona_high_id", nullable = false)
    private Long personaHighId;

    @Column(name = "speaker_id", nullable = false)
    private Long speakerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false)
    private DebateStage stage;

    @Column(name = "body", nullable = false, length = 300)
    private String body;

    protected DebateTurn() {
    }

    public DebateTurn(Long debateId, Short turnOrder, Long personaLowId, Long personaHighId, Long speakerId, DebateStage stage, String body) {
        this.debateId = debateId;
        this.turnOrder = turnOrder;
        this.personaLowId = personaLowId;
        this.personaHighId = personaHighId;
        this.speakerId = speakerId;
        this.stage = stage;
        this.body = body;
    }

    public Long getDebateId() {
        return debateId;
    }

    public Short getTurnOrder() {
        return turnOrder;
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

    public Long getSpeakerId() {
        return speakerId;
    }

    public void setSpeakerId(Long speakerId) {
        this.speakerId = speakerId;
    }

    public DebateStage getStage() {
        return stage;
    }

    public void setStage(DebateStage stage) {
        this.stage = stage;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
