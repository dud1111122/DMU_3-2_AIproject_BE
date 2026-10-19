package com.dmu.dshorts.domain.debate.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of {@link DebateTurn}. */
public class DebateTurnId implements Serializable {

    private Long debateId;
    private Short turnOrder;

    protected DebateTurnId() {
    }

    public DebateTurnId(Long debateId, Short turnOrder) {
        this.debateId = debateId;
        this.turnOrder = turnOrder;
    }

    public Long getDebateId() {
        return debateId;
    }

    public Short getTurnOrder() {
        return turnOrder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DebateTurnId other)) {
            return false;
        }
        return Objects.equals(debateId, other.debateId) && Objects.equals(turnOrder, other.turnOrder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(debateId, turnOrder);
    }
}
