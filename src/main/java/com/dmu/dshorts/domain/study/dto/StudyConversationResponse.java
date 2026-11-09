package com.dmu.dshorts.domain.study.dto;

import java.time.Instant;
import java.util.List;

/**
 * Conversation context + one page of messages (ascending by id).
 * Older page: {@code GET /study-conversations/{id}?beforeMessageId={nextBeforeMessageId}}.
 */
public record StudyConversationResponse(
        String id,
        String postId,
        Instant createdAt,
        Instant lastMessageAt,
        PostSummary post,
        PersonaSummary persona,
        String aiDisclosure,
        List<KeyPointSummary> keyPoints,
        List<StudyMessageResponse> messages,
        boolean hasMoreBefore,
        String nextBeforeMessageId
) {
    public record PostSummary(String id, String title, String summary) {
    }

    /** Public persona fields only; internal AI settings are never exposed. */
    public record PersonaSummary(String id, String username, String displayName, String era, String avatarPath) {
    }

    public record KeyPointSummary(String id, int order, String text) {
    }
}
