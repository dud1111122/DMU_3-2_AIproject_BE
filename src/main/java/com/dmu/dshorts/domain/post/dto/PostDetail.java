package com.dmu.dshorts.domain.post.dto;

import com.dmu.dshorts.domain.persona.dto.PersonaSummary;
import com.dmu.dshorts.domain.topic.dto.TopicSummary;

import java.time.Instant;
import java.util.List;

/** {@code GET /posts/{id}} (shape of BE_Endpoint.md §2 plus {@code publishedAt} and {@code likeCount}). */
public record PostDetail(
        String id,
        PersonaSummary persona,
        String title,
        String summary,
        String caption,
        String groundingMode,
        Instant publishedAt,
        List<TopicSummary> topics,
        List<SectionResponse> sections,
        List<KeyPointResponse> keyPoints,
        List<SourceResponse> sources,
        int estimatedReadSeconds,
        long likeCount,
        UserState userState) {
}
