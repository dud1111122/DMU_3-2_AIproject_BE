package com.dmu.dshorts.domain.generation.service;

import com.dmu.dshorts.domain.generation.seed.SeedData.KeyPointSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.PostSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.QuestionSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.SectionSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.SourceSeed;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Strict conversion of the AI's JSON object into a {@link PostSeed} draft. Wrong JSON types are rejected
 * ({@link InvalidPostDraftException}); rule checks are left to {@link PostDraftValidator}.
 * Normalisation: image fields are always null (image generation is not part of this command), an unparseable
 * URL becomes null, and the job's representative topic is always included in {@code topics}.
 */
public final class AiPostParser {

    private AiPostParser() {
    }

    public static PostSeed parse(JsonNode root, String personaUsername, String representativeTopic) {
        require(root != null && root.isObject(), "AI output must be a JSON object");

        Set<String> topics = new LinkedHashSet<>();
        topics.add(representativeTopic);
        for (JsonNode topic : optionalArray(root, "topics")) {
            topics.add(text(topic, "topics[]"));
        }

        List<SectionSeed> sections = new ArrayList<>();
        for (JsonNode node : array(root, "sections")) {
            requireObject(node, "sections[]");
            sections.add(new SectionSeed(optionalText(node, "title"), optionalText(node, "body"),
                    optionalText(node, "citationType"), optionalText(node, "quoteAttribution"),
                    null, optionalText(node, "imageAlt"), null, null, null, false, null,
                    optionalText(node, "imagePrompt")));
        }

        List<KeyPointSeed> keyPoints = new ArrayList<>();
        for (JsonNode node : array(root, "keyPoints")) {
            requireObject(node, "keyPoints[]");
            JsonNode q = node.get("question");
            requireObject(q, "keyPoints[].question");
            List<String> options = new ArrayList<>();
            for (JsonNode option : array(q, "options")) {
                options.add(text(option, "options[]"));
            }
            keyPoints.add(new KeyPointSeed(optionalText(node, "text"), optionalInt(node, "sectionOrder"),
                    new QuestionSeed(optionalText(q, "prompt"), options, requiredInt(q, "correctOptionOrder"),
                            optionalText(q, "explanation"))));
        }

        List<SourceSeed> sources = new ArrayList<>();
        for (JsonNode node : array(root, "sources")) {
            requireObject(node, "sources[]");
            String url = optionalText(node, "url");
            sources.add(new SourceSeed(optionalText(node, "title"), optionalText(node, "author"),
                    optionalText(node, "publisher"), PostDraftValidator.isHttpUrl(url) ? url.strip() : null,
                    optionalText(node, "publicationLabel"), optionalText(node, "note")));
        }

        return new PostSeed(personaUsername, List.copyOf(topics), optionalText(root, "title"),
                optionalText(root, "summary"), optionalText(root, "caption"), null, sections, keyPoints, sources);
    }

    private static Iterable<JsonNode> array(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        require(node != null && node.isArray(), field + " must be an array");
        return node;
    }

    private static Iterable<JsonNode> optionalArray(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            return List.of();
        }
        require(node.isArray(), field + " must be an array");
        return node;
    }

    private static String optionalText(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return text(node, field);
    }

    private static String text(JsonNode node, String field) {
        require(node != null && node.isTextual(), field + " must be a string");
        return node.asText();
    }

    private static Integer optionalInt(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        require(node.isIntegralNumber() && node.canConvertToInt(), field + " must be an integer");
        return node.intValue();
    }

    private static int requiredInt(JsonNode parent, String field) {
        Integer value = optionalInt(parent, field);
        require(value != null, field + " is required");
        return value;
    }

    private static void requireObject(JsonNode node, String field) {
        require(node != null && node.isObject(), field + " must be an object");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidPostDraftException(message);
        }
    }
}
