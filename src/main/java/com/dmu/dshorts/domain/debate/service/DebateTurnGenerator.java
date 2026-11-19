package com.dmu.dshorts.domain.debate.service;

import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.global.ai.AiClient;
import com.dmu.dshorts.global.ai.AiMessage;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Prompts, AI calls and output validation for live debates. Must be called outside any DB transaction.
 * Invalid output, empty output or a provider failure → 502 AI_FAILED (nothing is stored by the caller).
 */
@Component
public class DebateTurnGenerator {

    static final int SUMMARY_MAX_CHARS = 1000;

    private static final Logger log = LoggerFactory.getLogger(DebateTurnGenerator.class);

    private final AiClient aiClient;

    public DebateTurnGenerator(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    /** One transcript line for prompts: speaker label (persona display name or "사용자") and body. */
    public record Line(String speaker, String body) {
    }

    /** Summary text and the model that produced it. */
    public record Summary(String text, String model) {
    }

    /** 503 AI_NOT_CONFIGURED when AI is not set up. Checked before a stream is started. */
    void requireConfigured() {
        if (!aiClient.configured()) {
            throw new ApiException(ErrorCode.AI_NOT_CONFIGURED);
        }
    }

    /** Streams one AI turn through {@code onDelta} and returns the validated body (max 300 characters). */
    public String streamTurn(List<AiMessage> messages, Consumer<String> onDelta) {
        String text;
        try {
            text = aiClient.stream(messages, onDelta);
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Debate turn stream failed", ex);
            throw invalid("AI 응답을 받지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
        String body = text == null ? "" : text.strip();
        if (body.isEmpty()) {
            throw invalid("AI 발언이 비어 있습니다.");
        }
        if (body.codePointCount(0, body.length()) > DebateRules.TURN_MAX_CHARS) {
            throw invalid("AI 발언이 300자를 넘었습니다.");
        }
        return body;
    }

    /** One non-streaming call that summarizes the whole debate. */
    public Summary summarize(List<AiMessage> messages) {
        String text;
        try {
            text = aiClient.complete(messages, false);
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("Debate summary call failed", ex);
            throw invalid("AI 요약을 받지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
        String summary = text == null ? "" : text.strip();
        if (summary.isEmpty() || summary.codePointCount(0, summary.length()) > SUMMARY_MAX_CHARS) {
            throw invalid("AI 요약 길이가 올바르지 않습니다.");
        }
        return new Summary(summary, aiClient.modelName());
    }

    /** Prompt for the next AI turn spoken by {@code speaker} (one of {@code first}/{@code second}). */
    static List<AiMessage> turnMessages(Persona first, Persona second, Persona speaker, String topic,
                                        List<Line> transcript) {
        String label = speaker.getId().equals(first.getId()) ? "A" : "B";
        String system = """
                너는 교육용 서비스 '디쇼츠'의 가상 대담을 진행하는 작가다.
                이 대담은 실제 인물 간의 대화가 아니라 AI가 각 인물의 알려진 관점을 바탕으로 재구성한 가상 대화다.
                - 실제 인물의 발언·저작을 직접 인용한 것처럼 쓰지 않는다. 따옴표로 된 인용문이나 출처·URL을 만들지 않는다.
                - 생전 이후의 사건·현대 지식은 그 인물이 알았던 사실처럼 말하지 않는다.
                - 혐오·차별·위험 행위 조장, 상대 비하를 금지한다. 학습자가 쟁점을 이해하도록 쉽고 정중하게 쓴다.
                - 각 페르소나의 설정(관점, 말투, 지식 범위, 금지 사항, 대화 규칙)을 따른다.

                %s

                %s

                이번에 발언하는 사람: 페르소나 %s (%s)
                규칙:
                - 이번 발언자의 1인칭 발언만 쓴다. 상대방이나 사용자의 말을 대신 쓰지 않는다.
                - 핵심만 한국어 1~2문장, 120자 이내(공백 포함)로 쓴다. 주장 하나와 근거 하나면 충분하다. 인사·서론·장황한 부연을 쓰지 않는다.
                - JSON, 마크다운, 이름표(예: "이름:")를 붙이지 않고 발언 본문만 출력한다.
                - 대화 기록에 사용자 발언이 있으면 그 질문이나 의견에 먼저 답한 뒤 논의를 이어간다.
                - 직전 발언을 받아 상대 입장에 반박하거나 보완한다. 기록이 없으면 논제에 대한 첫 입장을 말한다.
                """.formatted(personaBlock("A", first), personaBlock("B", second), label, speaker.getDisplayName());
        String user = "논제: " + topic + "\n\n[대화 기록]\n" + transcriptBlock(transcript)
                + "\n\n위 흐름에 이어 " + speaker.getDisplayName() + "의 다음 발언을 써줘.";
        return List.of(AiMessage.system(system), AiMessage.user(user));
    }

    /** Prompt for the closing summary of the whole debate. */
    static List<AiMessage> summaryMessages(Persona first, Persona second, String topic, List<Line> transcript) {
        String system = """
                너는 교육용 서비스 '디쇼츠'의 가상 대담을 요약하는 편집자다.
                두 페르소나의 쟁점과 입장 차이를 중립적으로 정리한다.
                - 대화에 없는 내용을 덧붙이지 않는다. 인용 부호나 출처를 만들지 않는다.
                - 한국어 500자 이내 평문 한 단락으로 쓴다. JSON·마크다운·머리말 없이 요약 본문만 출력한다.

                %s

                %s
                """.formatted(personaBlock("A", first), personaBlock("B", second));
        String user = "논제: " + topic + "\n\n[대화 기록]\n" + transcriptBlock(transcript)
                + "\n\n위 대담의 핵심 쟁점과 두 입장의 차이를 요약해줘.";
        return List.of(AiMessage.system(system), AiMessage.user(user));
    }

    private static String personaBlock(String label, Persona persona) {
        return """
                [페르소나 %s]
                이름: %s
                시대: %s
                소개: %s
                관점: %s
                말투: %s
                지식 범위: %s
                금지 사항: %s
                대화 규칙: %s""".formatted(label, nullToEmpty(persona.getDisplayName()), nullToEmpty(persona.getEra()),
                nullToEmpty(persona.getBio()), nullToEmpty(persona.getPerspective()), nullToEmpty(persona.getVoice()),
                nullToEmpty(persona.getKnowledgeScope()), nullToEmpty(persona.getProhibitions()),
                nullToEmpty(persona.getConversationRules()));
    }

    private static String transcriptBlock(List<Line> transcript) {
        if (transcript.isEmpty()) {
            return "(아직 발언이 없습니다.)";
        }
        List<String> lines = new ArrayList<>(transcript.size());
        for (int i = 0; i < transcript.size(); i++) {
            Line line = transcript.get(i);
            lines.add("[" + (i + 1) + "] " + line.speaker() + ": " + line.body());
        }
        return String.join("\n", lines);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static ApiException invalid(String reason) {
        log.warn("Invalid debate AI output: {}", reason);
        return new ApiException(ErrorCode.AI_FAILED, "AI 대담 응답 형식이 올바르지 않습니다. 잠시 후 다시 시도해주세요.");
    }
}
