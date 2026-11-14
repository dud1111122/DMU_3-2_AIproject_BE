package com.dmu.dshorts.domain.generation.seed;

import java.util.List;

/** JSON shapes of {@code classpath:seed/catalog.json} and {@code classpath:seed/posts.json}. */
public final class SeedData {

    private SeedData() {
    }

    public record Catalog(List<TopicSeed> topics, List<PersonaSeed> personas) {
    }

    public record TopicSeed(String name, String description, int sortOrder) {
    }

    /** {@code topics} are topic names from the catalog. */
    public record PersonaSeed(String username, String displayName, String era, String bio,
                              String perspective, String voice, String knowledgeScope,
                              String prohibitions, String conversationRules, List<String> topics) {
    }

    public record Posts(List<PostSeed> posts) {
    }

    /**
     * A complete post. {@code topics} must be a subset of the persona's topics. Sections/key points/sources
     * get their order (1..N) from their list position. {@code publishedHoursAgo} spreads the feed order.
     */
    public record PostSeed(String personaUsername, List<String> topics, String title, String summary,
                           String caption, Integer publishedHoursAgo, List<SectionSeed> sections,
                           List<KeyPointSeed> keyPoints, List<SourceSeed> sources) {
    }

    /** {@code citationType} DIRECT_QUOTE requires {@code quoteAttribution}. Image requires imageAlt. */
    public record SectionSeed(String title, String body, String citationType, String quoteAttribution,
                              String imagePath, String imageAlt, String imageMime, Integer imageWidth,
                              Integer imageHeight, Boolean imageAiGenerated, String imageCredit,
                              String imagePrompt) {
    }

    /** {@code sectionOrder} optionally links the point to a card (1-based). */
    public record KeyPointSeed(String text, Integer sectionOrder, QuestionSeed question) {
    }

    /** Exactly 4 options; {@code correctOptionOrder} is 1..4. */
    public record QuestionSeed(String prompt, List<String> options, int correctOptionOrder, String explanation) {
    }

    /** Seeded sources are always {@code verified=false} (AI-suggested). */
    public record SourceSeed(String title, String author, String publisher, String url,
                             String publicationLabel, String note) {
    }
}
