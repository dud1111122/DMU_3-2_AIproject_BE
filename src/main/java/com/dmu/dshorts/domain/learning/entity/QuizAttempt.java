package com.dmu.dshorts.domain.learning.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="quiz_attempts")
public class QuizAttempt {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String userId; private String quizId; private Integer questionOrder; private Integer selectedOption; private boolean correct; private Instant createdAt=Instant.now();
    protected QuizAttempt() {}
    public QuizAttempt(String userId,String quizId,Integer questionOrder,Integer selectedOption,boolean correct){this.userId=userId;this.quizId=quizId;this.questionOrder=questionOrder;this.selectedOption=selectedOption;this.correct=correct;}
    public Long getId(){return id;} public String getUserId(){return userId;} public String getQuizId(){return quizId;} public Integer getQuestionOrder(){return questionOrder;} public Integer getSelectedOption(){return selectedOption;} public boolean isCorrect(){return correct;} public Instant getCreatedAt(){return createdAt;}
}
