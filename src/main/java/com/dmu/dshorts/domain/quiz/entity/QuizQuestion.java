package com.dmu.dshorts.domain.quiz.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name="quiz_questions")
public class QuizQuestion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(columnDefinition="TEXT") private String prompt;
    private Integer keyPointOrder;
    private Integer correctOption;
    @Column(columnDefinition="TEXT") private String explanation;
    @ElementCollection @CollectionTable(name="quiz_options",joinColumns=@JoinColumn(name="question_id"))
    @OrderColumn(name="option_order") @Column(name="option_text") private List<String> options=new ArrayList<>();
    protected QuizQuestion() {}
    public QuizQuestion(String prompt,Integer keyPointOrder,Integer correctOption,String explanation,List<String> options){this.prompt=prompt;this.keyPointOrder=keyPointOrder;this.correctOption=correctOption;this.explanation=explanation;this.options=new ArrayList<>(options);}
    public Long getId(){return id;} public String getPrompt(){return prompt;} public Integer getKeyPointOrder(){return keyPointOrder;} public Integer getCorrectOption(){return correctOption;} public String getExplanation(){return explanation;} public List<String> getOptions(){return options;}
}
