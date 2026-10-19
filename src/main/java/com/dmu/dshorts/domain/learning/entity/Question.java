package com.dmu.dshorts.domain.learning.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Table {@code questions}. One question per key point (the post id is the quiz unit). {@code correctOptionOrder} references an
 * existing option via a DEFERRABLE FK: insert the question and its 4 {@link AnswerOption}s in one transaction.
 * Question order = the linked key point's pointOrder. Never expose correctOptionOrder/explanation before submit.
 */
@Entity
@Table(name = "questions")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "key_point_id", nullable = false)
    private Long keyPointId;

    @Column(name = "prompt", nullable = false)
    private String prompt;

    @Column(name = "explanation", nullable = false)
    private String explanation;

    @Column(name = "correct_option_order", nullable = false)
    private short correctOptionOrder;

    protected Question() {
    }

    public Question(Long postId, Long keyPointId, String prompt, String explanation, short correctOptionOrder) {
        this.postId = postId;
        this.keyPointId = keyPointId;
        this.prompt = prompt;
        this.explanation = explanation;
        this.correctOptionOrder = correctOptionOrder;
    }

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public Long getKeyPointId() {
        return keyPointId;
    }

    public void setKeyPointId(Long keyPointId) {
        this.keyPointId = keyPointId;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public short getCorrectOptionOrder() {
        return correctOptionOrder;
    }

    public void setCorrectOptionOrder(short correctOptionOrder) {
        this.correctOptionOrder = correctOptionOrder;
    }
}
