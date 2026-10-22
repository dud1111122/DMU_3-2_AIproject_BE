package com.dmu.dshorts.domain.learning.dto;

import java.util.List;

/**
 * {@code POST /posts/{postId}/quiz/attempts} body:
 * {@code {"answers": [{"questionId": "401", "selectedOptionOrder": 2}, ...]}}.
 * Validated in the service so every problem is reported with its field path (e.g. {@code answers[1].questionId}).
 */
public record QuizSubmitRequest(List<Answer> answers) {

    public record Answer(String questionId, Integer selectedOptionOrder) {
    }
}
