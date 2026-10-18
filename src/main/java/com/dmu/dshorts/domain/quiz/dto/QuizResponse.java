package com.dmu.dshorts.domain.quiz.dto;

import java.util.List;
public record QuizResponse(String id,String postId,List<Question> questions) {
    public record Question(int order,String prompt,Integer keyPointOrder,List<String> options) {}
}
