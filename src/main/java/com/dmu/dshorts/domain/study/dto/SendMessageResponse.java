package com.dmu.dshorts.domain.study.dto;

import java.util.List;

/** Result of a successful member chat turn: both stored messages and the assessments updated by this turn. */
public record SendMessageResponse(
        StudyMessageResponse userMessage,
        StudyMessageResponse personaMessage,
        List<AssessmentResult> assessments
) {
    public record AssessmentResult(String keyPointId, String level, String feedback, String evidenceMessageId) {
    }
}
