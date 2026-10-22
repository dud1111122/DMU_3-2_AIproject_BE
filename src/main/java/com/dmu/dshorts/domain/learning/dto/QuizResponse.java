package com.dmu.dshorts.domain.learning.dto;

import java.util.List;

/**
 * {@code GET /posts/{postId}/quiz}. One question per key point, ordered by the key point order.
 * Never contains the correct option or the explanation.
 */
public record QuizResponse(String postId, int totalCount, List<QuizQuestion> questions) {

    public record QuizQuestion(String questionId, String keyPointId, int pointOrder, String prompt,
                               List<QuizOption> options) {
    }

    public record QuizOption(int optionOrder, String text) {
    }
}
