package com.dmu.dshorts.domain.learning.dto;

import com.dmu.dshorts.domain.learning.repository.QuizAttemptRepository.AttemptRow;
import com.dmu.dshorts.global.common.Ids;
import java.time.Instant;

/** Item of {@code GET /users/me/quiz-attempts}. */
public record QuizAttemptSummary(String attemptId, String postId, String postTitle, String personaId,
                                 String personaDisplayName, int correctCount, int totalCount, Instant submittedAt) {

    public static QuizAttemptSummary from(AttemptRow row) {
        return new QuizAttemptSummary(Ids.str(row.getId()), Ids.str(row.getPostId()), row.getPostTitle(),
                Ids.str(row.getPersonaId()), row.getPersonaDisplayName(), row.getCorrectCount(),
                row.getTotalCount(), row.getSubmittedAt());
    }
}
