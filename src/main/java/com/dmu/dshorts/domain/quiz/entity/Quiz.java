package com.dmu.dshorts.domain.quiz.entity;

import com.dmu.dshorts.domain.post.entity.Post;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name="quizzes")
public class Quiz {
    @Id private String id;
    @OneToOne(optional=false,fetch=FetchType.LAZY) private Post post;
    @OneToMany(cascade=CascadeType.ALL,orphanRemoval=true) @JoinColumn(name="quiz_id",nullable=false)
    @OrderColumn(name="question_order") private List<QuizQuestion> questions=new ArrayList<>();
    protected Quiz() {}
    public Quiz(String id,Post post,List<QuizQuestion> questions){this.id=id;this.post=post;this.questions=new ArrayList<>(questions);}
    public String getId(){return id;} public Post getPost(){return post;} public List<QuizQuestion> getQuestions(){return questions;}
}
