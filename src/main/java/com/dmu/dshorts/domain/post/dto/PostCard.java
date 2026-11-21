package com.dmu.dshorts.domain.post.dto;

import com.dmu.dshorts.domain.persona.dto.PersonaSummary;
import com.dmu.dshorts.domain.topic.dto.TopicSummary;

import java.time.Instant;
import java.util.List;

/**
 * Post item of feed / persona posts / search / saved lists: everything a carousel needs (cards with images,
 * persona, topics, like count, viewer state). Key points and sources are only in {@link PostDetail}.
 */
public record PostCard(
        String id,
        PersonaSummary persona,
        String title,
        String summary,
        String caption,
        String groundingMode,
        Instant publishedAt,
        List<TopicSummary> topics,
        List<SectionResponse> sections,
        int estimatedReadSeconds,
        long likeCount,
        UserState userState) {
}
