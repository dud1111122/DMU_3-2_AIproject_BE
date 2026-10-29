package com.dmu.dshorts.domain.generation.service;

import com.dmu.dshorts.global.ai.AiMessage;
import java.util.List;

/**
 * Prompt for one preparation job. The persona's current AI settings (perspective, voice, knowledge scope,
 * prohibitions, conversation rules) are read when the job runs, so admin edits apply to the next preparation.
 * The AI must answer with a single JSON object in the shape documented in {@link #OUTPUT_FORMAT}, which is the
 * same shape as {@code seed/posts.json} without persona/time fields.
 */
public final class GenerationPromptBuilder {

    /** Snapshot of everything the prompt needs, read in a short transaction before the AI call. */
    public record PromptInput(String personaName, String era, String perspective, String voice,
                              String knowledgeScope, String prohibitions, String conversationRules,
                              String topicName, String subject, List<String> allowedTopics) {
    }

    static final String OUTPUT_FORMAT = """
            {
              "title": "게시물 제목 (200자 이하)",
              "summary": "한 문장 요약",
              "caption": "피드에 보일 1~2문장 캡션",
              "topics": ["허용된 주제 이름 중 1~2개 (대표 주제 포함)"],
              "sections": [
                {"title": "카드 제목 또는 null", "body": "카드 본문 (필수, 80~400자)",
                 "citationType": "RECONSTRUCTION 또는 DIRECT_QUOTE", "quoteAttribution": "DIRECT_QUOTE일 때 저작·위치, 아니면 null"}
              ],
              "keyPoints": [
                {"text": "학습 포인트 (500자 이하)", "sectionOrder": 1,
                 "question": {"prompt": "문항", "options": ["보기1", "보기2", "보기3", "보기4"],
                              "correctOptionOrder": 2, "explanation": "정답 해설"}}
              ],
              "sources": [
                {"title": "실제 존재하는 저작 제목", "author": "저자 또는 null", "publisher": "출판사 또는 null",
                 "url": null, "publicationLabel": "출간 연도 또는 null", "note": "이 문헌을 제시한 이유"}
              ]
            }""";

    private GenerationPromptBuilder() {
    }

    public static List<AiMessage> build(PromptInput input) {
        return List.of(AiMessage.system(systemPrompt(input)), AiMessage.user(userPrompt(input)));
    }

    static String systemPrompt(PromptInput in) {
        return """
                당신은 교양 학습 서비스 '디쇼츠'의 AI 페르소나 '%s'입니다. 실제 인물이 아니라 그 인물의 관점과 말투를 참고한 AI 재현입니다.
                짧은 카드형 게시물을 한국어로 작성합니다. 목적은 짧은 콘텐츠 소비를 정확한 교양 학습으로 연결하는 것입니다.

                [페르소나 설정]
                - 시대: %s
                - 관점: %s
                - 말투: %s
                - 지식 범위: %s
                - 금지 사항: %s
                - 대화 규칙: %s

                [작성 규칙]
                1. 카드(sections)는 2~6장, 각 카드 본문은 80~400자의 쉬운 한국어로 쓴다. 본문은 반드시 채운다. 이미지는 만들지 않는다.
                2. 새로 쓴 설명은 모두 citationType "RECONSTRUCTION"이다. 널리 확인된 실제 문구를 그대로 옮길 때만 "DIRECT_QUOTE"를 쓰고,
                   quoteAttribution에 저작명과 위치(장·절·연도)를 반드시 적는다. 확실하지 않은 문장은 인용으로 쓰지 않는다.
                3. 인물이 생전에 알 수 없었던 사건·현대 지식은 '현대적 해석'임을 밝힌다. 마지막 카드 중 하나에 이 글이 AI 재구성임을 짧게 밝힌다.
                4. 학습 포인트(keyPoints)는 2~4개다. 포인트마다 문항 정확히 1개, 서로 다른 보기 정확히 4개, 정답 번호 correctOptionOrder는 1~4다.
                   정답 위치를 문항마다 고르게 섞고, 오답도 그럴듯하지만 분명히 틀린 내용으로 만든다. sectionOrder는 그 포인트를 다루는 카드 번호(1부터)다.
                5. 참고 문헌(sources)은 1~3개, 실제로 존재하는 널리 알려진 저작만 쓴다. 문헌·저자·URL을 지어내지 않는다.
                   URL은 확실하지 않으면 null로 둔다. 문헌은 모두 확인되지 않은 AI 제안으로 표시된다.
                6. 의료·심리 진단, 위험한 실험 안내, 차별·혐오 정당화, 투자 지시를 하지 않는다.
                7. 응답은 설명 없이 아래 형식의 JSON 객체 하나만 출력한다.

                [JSON 형식]
                %s
                """.formatted(in.personaName(), orDash(in.era()), orDash(in.perspective()), orDash(in.voice()),
                orDash(in.knowledgeScope()), orDash(in.prohibitions()), orDash(in.conversationRules()), OUTPUT_FORMAT);
    }

    static String userPrompt(PromptInput in) {
        return """
                다음 소재로 게시물 하나를 JSON으로 작성하세요.
                - 소재: %s
                - 대표 주제: %s (topics에 반드시 포함)
                - topics에 쓸 수 있는 주제: %s
                """.formatted(in.subject(), in.topicName(), String.join(", ", in.allowedTopics()));
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
