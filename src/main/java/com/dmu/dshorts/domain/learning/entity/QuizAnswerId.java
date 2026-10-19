package com.dmu.dshorts.domain.learning.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of {@link QuizAnswer}. */
public class QuizAnswerId implements Serializable {

    private Long attemptId;
    private Long questionId;

    protected QuizAnswerId() {
    }

    public QuizAnswerId(Long attemptId, Long questionId) {
        this.attemptId = attemptId;
        this.questionId = questionId;
    }

    public Long getAttemptId() {
        return attemptId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QuizAnswerId other)) {
            return false;
        }
        return Objects.equals(attemptId, other.attemptId) && Objects.equals(questionId, other.questionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attemptId, questionId);
    }
}
