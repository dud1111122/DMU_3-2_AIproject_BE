package com.dmu.dshorts.domain.learning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Table {@code answer_options}. Option 1..4 of a question; key is (questionId, optionOrder).
 */
@Entity
@Table(name = "answer_options")
@IdClass(AnswerOptionId.class)
public class AnswerOption {

    @Id
    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Id
    @Column(name = "option_order", nullable = false)
    private Short optionOrder;

    @Column(name = "text", nullable = false, length = 500)
    private String text;

    protected AnswerOption() {
    }

    public AnswerOption(Long questionId, Short optionOrder, String text) {
        this.questionId = questionId;
        this.optionOrder = optionOrder;
        this.text = text;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public Short getOptionOrder() {
        return optionOrder;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
