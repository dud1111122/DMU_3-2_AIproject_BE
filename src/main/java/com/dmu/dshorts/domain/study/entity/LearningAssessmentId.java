package com.dmu.dshorts.domain.study.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of {@link LearningAssessment}. */
public class LearningAssessmentId implements Serializable {

    private Long conversationId;
    private Long keyPointId;

    protected LearningAssessmentId() {
    }

    public LearningAssessmentId(Long conversationId, Long keyPointId) {
        this.conversationId = conversationId;
        this.keyPointId = keyPointId;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public Long getKeyPointId() {
        return keyPointId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LearningAssessmentId other)) {
            return false;
        }
        return Objects.equals(conversationId, other.conversationId) && Objects.equals(keyPointId, other.keyPointId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(conversationId, keyPointId);
    }
}
