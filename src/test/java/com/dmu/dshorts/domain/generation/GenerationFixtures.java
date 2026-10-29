package com.dmu.dshorts.domain.generation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A valid AI output (as a mutable map) that tests can break in one place. */
final class GenerationFixtures {

    private GenerationFixtures() {
    }

    static Map<String, Object> validAiPost() {
        Map<String, Object> post = new LinkedHashMap<>();
        post.put("title", "테스트용 생성 게시물");
        post.put("summary", "생성 흐름을 확인하는 요약입니다.");
        post.put("caption", "테스트 캡션입니다.");
        post.put("sections", new ArrayList<>(List.of(
                section("첫 카드", "AI가 재구성한 첫 번째 설명 카드입니다.", "RECONSTRUCTION", null),
                section(null, "“널리 알려진 문장.”", "DIRECT_QUOTE", "어떤 저작 §1 (1900)"),
                section("마지막 카드", "이 게시물은 AI 재구성입니다.", "RECONSTRUCTION", null))));
        post.put("keyPoints", new ArrayList<>(List.of(
                keyPoint("첫 번째 학습 포인트", 1, 2),
                keyPoint("두 번째 학습 포인트", 3, 4))));
        Map<String, Object> source = new HashMap<>();
        source.put("title", "실제 저작");
        source.put("author", "저자");
        source.put("publisher", null);
        source.put("url", "not a url");
        source.put("publicationLabel", "1900");
        source.put("note", "AI 제안");
        post.put("sources", new ArrayList<>(List.of(source)));
        return post;
    }

    static Map<String, Object> section(String title, String body, String citationType, String attribution) {
        Map<String, Object> section = new HashMap<>();
        section.put("title", title);
        section.put("body", body);
        section.put("citationType", citationType);
        section.put("quoteAttribution", attribution);
        return section;
    }

    static Map<String, Object> keyPoint(String text, Integer sectionOrder, int correct) {
        Map<String, Object> question = new HashMap<>();
        question.put("prompt", text + "에 대한 문항");
        question.put("options", new ArrayList<>(List.of("보기 A", "보기 B", "보기 C", "보기 D")));
        question.put("correctOptionOrder", correct);
        question.put("explanation", "해설");
        Map<String, Object> point = new HashMap<>();
        point.put("text", text);
        point.put("sectionOrder", sectionOrder);
        point.put("question", question);
        return point;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> firstQuestion(Map<String, Object> post) {
        List<Map<String, Object>> points = (List<Map<String, Object>>) post.get("keyPoints");
        return (Map<String, Object>) points.get(0).get("question");
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> sections(Map<String, Object> post) {
        return (List<Map<String, Object>>) post.get("sections");
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> keyPoints(Map<String, Object> post) {
        return (List<Map<String, Object>>) post.get("keyPoints");
    }
}
