package com.dmu.dshorts.domain.activity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Table {@code follows}. Member follows a persona.
 */
@Entity
@Table(name = "follows")
@IdClass(FollowId.class)
public class Follow {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Id
    @Column(name = "persona_id", nullable = false)
    private Long personaId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Follow() {
    }

    public Follow(Long userId, Long personaId) {
        this.userId = userId;
        this.personaId = personaId;
    }

    @PrePersist
    protected void onPersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
    }

    public Long getUserId() {
        return userId;
    }

    public Long getPersonaId() {
        return personaId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
