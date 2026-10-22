package com.dmu.dshorts.domain.learning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Result of a quiz submission. {@code attemptId} is null and {@code persisted} false for guests.
 * {@code results} follow the key point order.
 */
public record QuizSubmitResponse(String attemptId, boolean persisted, int correctCount, int totalCount,
                                 List<QuestionResult> results) {

    public record QuestionResult(String questionId, String keyPointId, int pointOrder, int selectedOptionOrder,
                                 @JsonProperty("isCorrect") boolean isCorrect, int correctOptionOrder,
                                 String explanation) {
    }
}
