package com.dmu.dshorts.domain.learning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Table {@code quiz_answers}. Answer to one question within an attempt; composite FKs keep attempt/question/option on the same post.
 */
@Entity
@Table(name = "quiz_answers")
@IdClass(QuizAnswerId.class)
public class QuizAnswer {

    @Id
    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Id
    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "selected_option_order", nullable = false)
    private short selectedOptionOrder;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    protected QuizAnswer() {
    }

    public QuizAnswer(Long attemptId, Long questionId, Long postId, short selectedOptionOrder, boolean correct) {
        this.attemptId = attemptId;
        this.questionId = questionId;
        this.postId = postId;
        this.selectedOptionOrder = selectedOptionOrder;
        this.correct = correct;
    }

    public Long getAttemptId() {
        return attemptId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public short getSelectedOptionOrder() {
        return selectedOptionOrder;
    }

    public void setSelectedOptionOrder(short selectedOptionOrder) {
        this.selectedOptionOrder = selectedOptionOrder;
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }
}
