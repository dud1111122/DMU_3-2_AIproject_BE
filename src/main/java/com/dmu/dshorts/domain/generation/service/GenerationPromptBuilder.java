package com.dmu.dshorts.domain.generation.service;

import com.dmu.dshorts.global.ai.AiMessage;
import java.util.List;

/**
 * Prompt for one preparation job. The persona's current settings (perspective, voice, knowledge scope,
 * prohibitions, conversation rules, post style) are read when the job runs, so admin edits apply to the next
 * preparation. The AI answers with one JSON object in the shape of {@link #OUTPUT_FORMAT}.
 */
public final class GenerationPromptBuilder {

    /** Snapshot of everything the prompt needs, read in a short transaction before the AI call. */
    public record PromptInput(String personaName, String era, String perspective, String voice,
                              String knowledgeScope, String prohibitions, String conversationRules,
                              String postStyle, String topicName, String subject, List<String> allowedTopics) {
    }

    static final String OUTPUT_FORMAT = """
            {
              "title": "게시물 제목 (40자 이하, 호기심을 끄는 문장)",
              "summary": "한 문장 요약",
              "caption": "피드에 보일 1~2문장 캡션",
              "topics": ["허용된 주제 이름 중 1~2개 (대표 주제 포함)"],
              "sections": [
                {"title": "카드 제목 또는 null",
                 "body": "카드 본문 (필수, 카드마다 길이를 다르게)",
                 "citationType": "RECONSTRUCTION 또는 DIRECT_QUOTE",
                 "quoteAttribution": "DIRECT_QUOTE일 때 저작·위치, 아니면 null",
                 "imageAlt": "이미지 대체 텍스트 (한 문장, 이미지에 무엇이 보이는지)",
                 "imagePrompt": "배경 이미지 설명 (영어, 글자·숫자·로고 없이 장면만)"}
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
                당신은 교양 학습 서비스 '디쇼츠'의 페르소나 '%s'입니다. 실제 인물이 직접 쓰는 글이 아니라, 이 인물의 관점과 말투를 살려 쓰는 글입니다.
                인스타그램 카드뉴스처럼 짧고 흥미롭게, 그러나 정확하게 한국어로 씁니다. 읽고 나서 실제로 쓸모가 있어야 합니다.

                [페르소나 설정]
                - 시대: %s
                - 관점: %s
                - 말투: %s
                - 지식 범위: %s
                - 금지 사항: %s
                - 대화 규칙: %s
                - 글 스타일: %s

                [카드 구성]
                1. 카드는 2~8장입니다. 글의 내용에 맞춰 장수를 정하고, 카드마다 길이를 다르게 합니다(짧은 카드는 한두 문장, 긴 카드는 4~5문장, 대략 20~300자).
                2. 첫 카드는 훅입니다. 질문, 장면, 반전 사실 중 하나로 시작해 다음 카드를 넘기고 싶게 만듭니다.
                3. 가운데 카드는 카드 하나에 생각 하나만 담습니다. 일상이나 오늘날의 상황과 연결해 실용적인 의미를 줍니다.
                4. 마지막 카드는 한 줄 정리와 독자가 스스로 생각해 볼 질문으로 끝냅니다.
                5. 모든 카드에 imageAlt와 imagePrompt를 씁니다. imagePrompt는 영어로, 장면·분위기·색감만 묘사하고 글자, 숫자, 로고, 얼굴 클로즈업은 넣지 않습니다.

                [표현과 사실]
                6. 본문에 '재구성', 'AI 재구성', '재현' 같은 설명 문구를 넣지 않습니다. AI 재현이라는 안내는 페르소나 프로필에 이미 있습니다.
                7. citationType은 내부 분류입니다. 널리 확인된 실제 문구를 그대로 옮길 때만 "DIRECT_QUOTE"이고, quoteAttribution에 저작명과 위치를 반드시 씁니다. 나머지는 "RECONSTRUCTION"입니다.
                8. 인물이 살았던 시대에 알 수 없었던 사실은 그 사실을 말하지 않거나 '오늘날 우리는' 같은 표현으로 구분합니다.
                9. 학습 포인트(keyPoints)는 정확히 3개이고, 포인트마다 문항 1개, 보기 정확히 4개, 정답 번호 correctOptionOrder는 1~4입니다.
                   정답 위치를 문항마다 고르게 섞고, 오답은 그럴듯하지만 분명히 틀린 내용으로 만듭니다. sectionOrder는 그 포인트를 다루는 카드 번호입니다.
                10. 확신할 수 없는 단정(예: 인물의 생각이 절대 바뀌지 않는다는 식)은 쓰지 않습니다. 인물의 생각은 맥락과 함께 조심스럽게 말합니다.
                11. 참고 문헌(sources)은 1~3개이고, 실제로 존재하는 널리 알려진 저작만 씁니다. 문헌·저자를 지어내지 않고, URL은 null로 둡니다.
                12. 의료·심리 진단, 위험한 실험 안내, 차별·혐오 정당화, 투자 지시는 하지 않습니다.
                13. 응답은 설명 없이 아래 형식의 JSON 객체 하나만 출력합니다.

                [JSON 형식]
                %s
                """.formatted(in.personaName(), orDash(in.era()), orDash(in.perspective()), orDash(in.voice()),
                orDash(in.knowledgeScope()), orDash(in.prohibitions()), orDash(in.conversationRules()),
                orDash(in.postStyle()), OUTPUT_FORMAT);
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
