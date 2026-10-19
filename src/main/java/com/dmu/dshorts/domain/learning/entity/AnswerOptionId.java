package com.dmu.dshorts.domain.learning.entity;

import java.io.Serializable;
import java.util.Objects;

/** Composite key of {@link AnswerOption}. */
public class AnswerOptionId implements Serializable {

    private Long questionId;
    private Short optionOrder;

    protected AnswerOptionId() {
    }

    public AnswerOptionId(Long questionId, Short optionOrder) {
        this.questionId = questionId;
        this.optionOrder = optionOrder;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public Short getOptionOrder() {
        return optionOrder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AnswerOptionId other)) {
            return false;
        }
        return Objects.equals(questionId, other.questionId) && Objects.equals(optionOrder, other.optionOrder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questionId, optionOrder);
    }
}
