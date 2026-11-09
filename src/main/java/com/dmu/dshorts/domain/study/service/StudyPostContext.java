package com.dmu.dshorts.domain.study.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Detached snapshot of everything the chat prompt needs, loaded inside a read-only transaction so the AI call
 * can run with no transaction (and no lazy entity) open.
 */
public record StudyPostContext(
        Long postId,
        String title,
        String summary,
        PersonaSettings persona,
        List<Section> sections,
        List<Point> keyPoints,
        List<Source> sources
) {
    public record PersonaSettings(
            Long id,
            String displayName,
            String era,
            String aiDisclosure,
            String perspective,
            String voice,
            String knowledgeScope,
            String prohibitions,
            String conversationRules
    ) {
    }

    public record Section(int order, String title, String body, String citationType, String quoteAttribution) {
    }

    public record Point(Long id, int order, String text) {
    }

    public record Source(String title, String author, String publisher, String publicationLabel, String url,
                        boolean verified) {
    }

    public Set<Long> keyPointIds() {
        return keyPoints.stream().map(Point::id).collect(Collectors.toUnmodifiableSet());
    }
}
