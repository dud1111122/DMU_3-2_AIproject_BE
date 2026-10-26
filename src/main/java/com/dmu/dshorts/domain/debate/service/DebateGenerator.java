package com.dmu.dshorts.domain.debate.service;

import com.dmu.dshorts.domain.debate.entity.DebateStage;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.global.ai.AiClient;
import com.dmu.dshorts.global.ai.AiMessage;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Builds the debate prompt, makes exactly one AI call and validates the output.
 * Must be called outside any DB transaction. Invalid output → 502 AI_FAILED.
 */
@Component
public class DebateGenerator {

    static final int MAX_TURNS = 6;
    static final int TURN_MAX_CHARS = 300;
    static final int SUMMARY_MAX_CHARS = 1000;

    private static final Logger log = LoggerFactory.getLogger(DebateGenerator.class);

    private final AiClient aiClient;

    public DebateGenerator(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    public record GeneratedTurn(Long speakerId, DebateStage stage, String body) {
    }

    public record GeneratedDebate(List<GeneratedTurn> turns, String summary, String aiModel) {
    }

    /** {@code first} opens the debate. */
    public GeneratedDebate generate(Persona first, Persona second, String topic) {
        List<AiMessage> messages = List.of(
                AiMessage.system(systemPrompt(first, second)),
                AiMessage.user(userPrompt(first, second, topic)));
        JsonNode root;
        try {
            root = aiClient.completeJson(messages);
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Debate AI call failed", ex);
            throw new ApiException(ErrorCode.AI_FAILED);
        }
        return new GeneratedDebate(validateTurns(root, first.getId(), second.getId()),
                validateSummary(root), aiClient.modelName());
    }

    private static String systemPrompt(Persona first, Persona second) {
        return """
                너는 교육용 서비스 '디쇼츠'에서 두 역사적 인물 AI 페르소나의 가상 대담을 구성하는 작가다.
                이 대담은 실제 인물 간의 대화가 아니라 AI가 각 인물의 알려진 관점을 바탕으로 재구성한 가상 대화다.
                - 실제 인물의 발언·저작을 직접 인용한 것처럼 쓰지 말고, 따옴표로 된 인용문이나 출처·URL을 만들지 않는다.
                - 생전 이후의 사건·현대 지식은 그 인물이 알았던 사실처럼 말하지 않는다.
                - 혐오·차별·위험 행위 조장, 상대 비하를 금지한다. 학습자가 쟁점을 이해하도록 쉽고 정중하게 쓴다.
                - 각 페르소나의 설정(관점, 말투, 지식 범위, 금지 사항, 대화 규칙)을 따른다.

                %s

                %s

                출력은 반드시 아래 형식의 JSON 객체 하나만 쓴다. 다른 텍스트는 쓰지 않는다.
                {"turns":[{"personaId":"<ID>","stage":"OPENING|REBUTTAL|CLOSING","body":"<발언>"}],"summary":"<쟁점 요약>"}
                규칙:
                - personaId는 위 두 페르소나의 ID 문자열 중 하나만 쓴다.
                - 순서는 OPENING(입장) → REBUTTAL(반박) → CLOSING(마무리)이며, 각 단계에서 두 사람이 한 번씩 말한다.
                - 각 단계에서 먼저 말하는 사람은 페르소나 A(ID %d)다. 발언은 최대 6개다.
                - 같은 사람이 같은 단계에서 두 번 말하지 않는다.
                - 각 body는 한국어 300자 이하(공백 포함), 1인칭 발언으로 쓴다.
                - summary는 두 입장의 핵심 쟁점과 차이를 한국어 500자 이내로 중립적으로 요약한다.
                """.formatted(personaBlock("A", first), personaBlock("B", second), first.getId());
    }

    private static String personaBlock(String label, Persona persona) {
        return """
                [페르소나 %s] ID: %d
                이름: %s
                시대: %s
                소개: %s
                관점: %s
                말투: %s
                지식 범위: %s
                금지 사항: %s
                대화 규칙: %s""".formatted(label, persona.getId(), persona.getDisplayName(),
                nullToEmpty(persona.getEra()), persona.getBio(), persona.getPerspective(), persona.getVoice(),
                persona.getKnowledgeScope(), persona.getProhibitions(), persona.getConversationRules());
    }

    private static String userPrompt(Persona first, Persona second, String topic) {
        return "논제: " + topic + "\n" + first.getDisplayName() + "(ID " + first.getId() + ")과 "
                + second.getDisplayName() + "(ID " + second.getId() + ")의 가상 대담을 JSON으로 작성해줘.";
    }

    static List<GeneratedTurn> validateTurns(JsonNode root, Long firstId, Long secondId) {
        JsonNode turns = root == null ? null : root.get("turns");
        if (turns == null || !turns.isArray() || turns.isEmpty()) {
            throw invalid("turns가 없습니다.");
        }
        if (turns.size() > MAX_TURNS) {
            throw invalid("발언이 6개를 넘었습니다.");
        }
        List<GeneratedTurn> result = new ArrayList<>(turns.size());
        Set<String> speakerStages = new HashSet<>();
        Set<Long> speakers = new HashSet<>();
        DebateStage previous = DebateStage.OPENING;
        for (JsonNode turn : turns) {
            if (!turn.isObject()) {
                throw invalid("발언 형식이 올바르지 않습니다.");
            }
            Long speaker = parseSpeaker(turn.get("personaId"));
            if (speaker == null || (!speaker.equals(firstId) && !speaker.equals(secondId))) {
                throw invalid("참가자가 아닌 발언자가 있습니다.");
            }
            DebateStage stage = parseStage(turn.get("stage"));
            if (stage.ordinal() < previous.ordinal()) {
                throw invalid("발언 단계 순서가 올바르지 않습니다.");
            }
            previous = stage;
            if (!speakerStages.add(speaker + ":" + stage)) {
                throw invalid("같은 단계에 같은 발언자가 두 번 나왔습니다.");
            }
            JsonNode bodyNode = turn.get("body");
            String body = bodyNode != null && bodyNode.isTextual() ? bodyNode.asText().strip() : "";
            if (body.isEmpty() || body.codePointCount(0, body.length()) > TURN_MAX_CHARS) {
                throw invalid("발언 길이가 올바르지 않습니다.");
            }
            speakers.add(speaker);
            result.add(new GeneratedTurn(speaker, stage, body));
        }
        if (speakers.size() != 2) {
            throw invalid("두 참가자가 모두 발언해야 합니다.");
        }
        return result;
    }

    static String validateSummary(JsonNode root) {
        JsonNode node = root.get("summary");
        String summary = node != null && node.isTextual() ? node.asText().strip() : "";
        if (summary.isEmpty() || summary.codePointCount(0, summary.length()) > SUMMARY_MAX_CHARS) {
            throw invalid("쟁점 요약이 올바르지 않습니다.");
        }
        return summary;
    }

    private static Long parseSpeaker(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText().strip());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static DebateStage parseStage(JsonNode node) {
        if (node == null || !node.isTextual()) {
            throw invalid("발언 단계가 없습니다.");
        }
        try {
            return DebateStage.valueOf(node.asText().strip());
        } catch (IllegalArgumentException ex) {
            throw invalid("알 수 없는 발언 단계입니다.");
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static ApiException invalid(String reason) {
        log.warn("Invalid debate AI output: {}", reason);
        return new ApiException(ErrorCode.AI_FAILED, "AI 대담 결과 형식이 올바르지 않습니다. 잠시 후 다시 시도해주세요.");
    }
}
