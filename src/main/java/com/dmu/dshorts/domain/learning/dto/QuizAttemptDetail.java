package com.dmu.dshorts.domain.learning.dto;

import com.dmu.dshorts.domain.learning.dto.QuizResponse.QuizOption;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/** {@code GET /users/me/quiz-attempts/{id}}: the stored attempt with per-question answer, result and explanation. */
public record QuizAttemptDetail(String attemptId, String postId, String postTitle, String personaId,
                                String personaDisplayName, int correctCount, int totalCount, Instant submittedAt,
                                List<AttemptQuestion> questions) {

    public record AttemptQuestion(String questionId, String keyPointId, int pointOrder, String keyPointText,
                                  String prompt, List<QuizOption> options, int selectedOptionOrder,
                                  @JsonProperty("isCorrect") boolean isCorrect, int correctOptionOrder,
                                  String explanation) {
    }
}
