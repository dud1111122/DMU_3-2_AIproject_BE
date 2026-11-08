package com.dmu.dshorts.domain.study.dto;

import java.time.Instant;
import java.util.List;

/**
 * Latest assessment per key point of the conversation's post. Key points not assessed yet have
 * {@code level/feedback/evidenceMessageId/updatedAt = null}.
 */
public record AssessmentResponse(
        String conversationId,
        String postId,
        List<Item> items
) {
    public record Item(
            String keyPointId,
            int order,
            String text,
            String level,
            String feedback,
            String evidenceMessageId,
            Instant updatedAt
    ) {
    }
}
