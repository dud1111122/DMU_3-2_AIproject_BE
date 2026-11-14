package com.dmu.dshorts.domain.generation.service;

import com.dmu.dshorts.domain.generation.seed.SeedData.KeyPointSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.PostSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.QuestionSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.SectionSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.SourceSeed;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Content rules from 기획.md / DB_설계.md that the DB cannot check by itself (row counts, required texts, lengths).
 * The single gate before a post is saved, used by both the dev seed and AI preparation.
 *
 * <ul>
 *   <li>title 1..200 chars; topics non-empty and all within the persona's allowed topics</li>
 *   <li>2..8 cards; body required (≤ {@value #MAX_SECTION_BODY} chars); DIRECT_QUOTE requires quoteAttribution;
 *       an image path requires alt text</li>
 *   <li>2..4 key points (≤500 chars), optional sectionOrder within the cards; exactly one question per point
 *       with exactly 4 distinct non-blank options (≤500 chars) and correctOptionOrder 1..4</li>
 *   <li>1..5 sources with a title; url null or an absolute http(s) URL</li>
 * </ul>
 */
public final class PostDraftValidator {

    public static final int MIN_SECTIONS = 2;
    public static final int MAX_SECTIONS = 8;
    public static final int MIN_KEY_POINTS = 2;
    public static final int MAX_KEY_POINTS = 4;
    public static final int OPTION_COUNT = 4;
    public static final int MAX_SOURCES = 5;
    public static final int MAX_SECTION_BODY = 2000;

    private PostDraftValidator() {
    }

    /**
     * @param allowedTopics topic names the post may be linked to (the persona's active topics)
     * @throws InvalidPostDraftException naming the first broken rule
     */
    public static void validate(PostSeed post, Set<String> allowedTopics) {
        require(post != null, "post is missing");
        require(notBlank(post.title()) && post.title().length() <= 200, "title must be 1..200 chars");
        require(post.topics() != null && !post.topics().isEmpty(), "topics must not be empty");
        require(allowedTopics != null && allowedTopics.containsAll(post.topics()),
                "topics must be the persona's active topics: " + post.topics());

        List<SectionSeed> sections = post.sections();
        int sectionCount = sections == null ? 0 : sections.size();
        require(sectionCount >= MIN_SECTIONS && sectionCount <= MAX_SECTIONS,
                "needs " + MIN_SECTIONS + ".." + MAX_SECTIONS + " sections");
        for (int i = 0; i < sectionCount; i++) {
            SectionSeed section = sections.get(i);
            String label = "section " + (i + 1);
            require(section != null, label + " is missing");
            require(notBlank(section.body()), label + ": body required");
            require(section.body().length() <= MAX_SECTION_BODY, label + ": body too long");
            require(section.title() == null || section.title().length() <= 200, label + ": title too long");
            String citation = section.citationType();
            require(citation == null || "RECONSTRUCTION".equals(citation) || "DIRECT_QUOTE".equals(citation),
                    label + ": citationType must be RECONSTRUCTION or DIRECT_QUOTE");
            require(!"DIRECT_QUOTE".equals(citation) || notBlank(section.quoteAttribution()),
                    label + ": DIRECT_QUOTE needs quoteAttribution");
            require(section.imagePath() == null || notBlank(section.imageAlt()), label + ": image needs imageAlt");
            require(section.imageAlt() == null || section.imageAlt().length() <= 300, label + ": imageAlt too long");
        }

        List<KeyPointSeed> points = post.keyPoints();
        int pointCount = points == null ? 0 : points.size();
        require(pointCount >= MIN_KEY_POINTS && pointCount <= MAX_KEY_POINTS,
                "needs " + MIN_KEY_POINTS + ".." + MAX_KEY_POINTS + " key points");
        for (int i = 0; i < pointCount; i++) {
            KeyPointSeed point = points.get(i);
            String label = "key point " + (i + 1);
            require(point != null, label + " is missing");
            require(notBlank(point.text()) && point.text().length() <= 500, label + ": text must be 1..500 chars");
            require(point.sectionOrder() == null
                    || (point.sectionOrder() >= 1 && point.sectionOrder() <= sectionCount),
                    label + ": sectionOrder out of range");
            QuestionSeed question = point.question();
            require(question != null, label + ": needs exactly one question");
            require(notBlank(question.prompt()), label + ": question prompt required");
            require(notBlank(question.explanation()), label + ": question explanation required");
            require(question.options() != null && question.options().size() == OPTION_COUNT,
                    label + ": question needs exactly " + OPTION_COUNT + " options");
            Set<String> distinct = new HashSet<>();
            for (String option : question.options()) {
                require(notBlank(option) && option.length() <= 500, label + ": option must be 1..500 chars");
                require(distinct.add(option.strip()), label + ": options must be distinct");
            }
            require(question.correctOptionOrder() >= 1 && question.correctOptionOrder() <= OPTION_COUNT,
                    label + ": correctOptionOrder must be 1.." + OPTION_COUNT);
        }

        List<SourceSeed> sources = post.sources();
        int sourceCount = sources == null ? 0 : sources.size();
        require(sourceCount >= 1 && sourceCount <= MAX_SOURCES, "needs 1.." + MAX_SOURCES + " sources");
        for (int i = 0; i < sourceCount; i++) {
            SourceSeed source = sources.get(i);
            String label = "source " + (i + 1);
            require(source != null, label + " is missing");
            require(notBlank(source.title()) && source.title().length() <= 300, label + ": title must be 1..300 chars");
            require(source.author() == null || source.author().length() <= 200, label + ": author too long");
            require(source.publisher() == null || source.publisher().length() <= 200, label + ": publisher too long");
            require(source.publicationLabel() == null || source.publicationLabel().length() <= 50,
                    label + ": publicationLabel too long");
            require(source.url() == null || isHttpUrl(source.url()), label + ": url must be null or http(s)");
        }
    }

    /** True for an absolute http/https URL with a host. */
    public static boolean isHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(value.strip());
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && uri.getHost() != null;
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidPostDraftException(message);
        }
    }
}
