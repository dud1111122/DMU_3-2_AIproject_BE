package com.dmu.dshorts.domain.activity.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of {@link Follow}. */
public class FollowId implements Serializable {

    private Long userId;
    private Long personaId;

    protected FollowId() {
    }

    public FollowId(Long userId, Long personaId) {
        this.userId = userId;
        this.personaId = personaId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getPersonaId() {
        return personaId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FollowId other)) {
            return false;
        }
        return Objects.equals(userId, other.userId) && Objects.equals(personaId, other.personaId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, personaId);
    }
}
