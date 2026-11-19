package com.dmu.dshorts.domain.debate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dmu.dshorts.domain.debate.service.DebateRules;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.global.ai.AiMessage;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/** User messages, streamed AI turns, speaker alternation, the turn limit and the in-progress lock. */
class DebateTurnIntegrationTest extends DebateTestSupport {

    private static final int LIMIT = DebateRules.TURN_LIMIT;

    @Test
    void aiTurnsAlternateAndStreamDeltasThenTurnEvent() throws Exception {
        MockHttpSession member = newMember();
        Persona a = persona();
        Persona b = persona();
        String id = newDebate(member, a, b);

        postMessage(member, id, "  질문이 있어요?  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.order").value(1))
                .andExpect(jsonPath("$.data.speakerType").value("USER"))
                .andExpect(jsonPath("$.data.body").value("질문이 있어요?"));

        ArgumentCaptor<List<AiMessage>> prompts = newPromptCaptor();
        stubStream("첫 번째 ", "입장입니다.");
        List<Sse> first = nextTurn(member, id);
        assertThat(names(first)).containsExactly("delta", "delta", "turn");
        assertThat(first.get(0).data().get("text").asText()).isEqualTo("첫 번째 ");
        assertThat(first.get(2).data().get("order").asInt()).isEqualTo(2);
        assertThat(first.get(2).data().get("speakerType").asText()).isEqualTo("PERSONA");
        assertThat(first.get(2).data().get("personaId").asText()).isEqualTo(String.valueOf(a.getId()));
        assertThat(first.get(2).data().get("body").asText()).isEqualTo("첫 번째 입장입니다.");

        List<Sse> second = nextTurn(member, id);
        assertThat(second.get(second.size() - 1).data().get("personaId").asText())
                .isEqualTo(String.valueOf(b.getId()));
        assertThat(second.get(second.size() - 1).data().get("order").asInt()).isEqualTo(3);

        postMessage(member, id, "두 번째 질문").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.order").value(4));
        List<Sse> third = nextTurn(member, id);
        assertThat(third.get(third.size() - 1).data().get("personaId").asText())
                .isEqualTo(String.valueOf(a.getId()));
        assertThat(third.get(third.size() - 1).data().get("order").asInt()).isEqualTo(5);

        // the prompts name the speaker alternately (A, B, A) and carry the user's messages in order
        verify(aiClient, times(3)).stream(prompts.capture(), any());
        List<List<AiMessage>> calls = prompts.getAllValues();
        assertThat(calls.get(0).get(0).content()).contains("이번에 발언하는 사람: 페르소나 A");
        assertThat(calls.get(1).get(0).content()).contains("이번에 발언하는 사람: 페르소나 B");
        assertThat(calls.get(2).get(0).content()).contains("이번에 발언하는 사람: 페르소나 A");
        assertThat(calls.get(1).get(1).content()).contains("[1] 사용자: 질문이 있어요?");
        assertThat(calls.get(2).get(1).content()).contains("[4] 사용자: 두 번째 질문");

        JsonNode detail = debateJson(member, id);
        assertThat(detail.at("/data/turnCount").asInt()).isEqualTo(5);
        assertThat(detail.at("/data/remainingTurns").asInt()).isEqualTo(25);
        assertThat(detail.at("/data/turns").size()).isEqualTo(5);
        assertThat(detail.at("/data/turns/0/speakerType").asText()).isEqualTo("USER");
        assertThat(detail.at("/data/turns/0/personaId").isNull()).isTrue();
        assertThat(detail.at("/data/turns/1/speakerType").asText()).isEqualTo("PERSONA");
        assertThat(detail.at("/data/turns/2/personaId").asText()).isEqualTo(String.valueOf(b.getId()));
        assertThat(detail.at("/data/turns/3/speakerType").asText()).isEqualTo("USER");
        assertThat(detail.at("/data/turns/4/personaId").asText()).isEqualTo(String.valueOf(a.getId()));
    }

    @Test
    void messageLengthIsCheckedAfterTrim() throws Exception {
        MockHttpSession member = newMember();
        String id = newDebate(member, persona(), persona());

        postMessage(member, id, "가".repeat(301))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value("content"));
        postMessage(member, id, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value("content"));
        postMessage(member, id, " " + "나".repeat(300) + " ").andExpect(status().isCreated());
        assertThat(storedTurnCount(id)).isEqualTo(1);
    }

    @Test
    void invalidAiOutputIsAnErrorEventAndSavesNothing() throws Exception {
        MockHttpSession member = newMember();
        String id = newDebate(member, persona(), persona());

        stubStream("가".repeat(301));
        List<Sse> tooLong = nextTurn(member, id);
        assertThat(names(tooLong)).doesNotContain("turn").endsWith("error");
        assertThat(errorCode(tooLong)).isEqualTo("AI_FAILED");

        stubStream("   ");
        List<Sse> empty = nextTurn(member, id);
        assertThat(names(empty)).doesNotContain("turn").endsWith("error");
        assertThat(errorCode(empty)).isEqualTo("AI_FAILED");

        assertThat(storedTurnCount(id)).isZero();
        assertThat(storedTurnRows(id)).isZero();
    }

    @Test
    void aiFailureIsAnErrorEventAndSavesNothing() throws Exception {
        MockHttpSession member = newMember();
        String id = newDebate(member, persona(), persona());

        stubStreamFailure(new ApiException(ErrorCode.AI_FAILED));
        List<Sse> failed = nextTurn(member, id);
        assertThat(names(failed)).containsExactly("error");
        assertThat(errorCode(failed)).isEqualTo("AI_FAILED");

        stubStreamFailure(new IllegalStateException("socket closed"));
        List<Sse> crashed = nextTurn(member, id);
        assertThat(names(crashed)).containsExactly("error");
        assertThat(errorCode(crashed)).isEqualTo("AI_FAILED");

        assertThat(storedTurnRows(id)).isZero();
        assertThat(debateJson(member, id).at("/data/turnCount").asInt()).isZero();
    }

    @Test
    void twelfthAiTurnEndsTheDebateAndStoresSummary() throws Exception {
        MockHttpSession member = newMember();
        String id = newDebate(member, persona(), persona());
        stubStream("발언");
        stubSummary("쟁점 요약");

        for (int i = 0; i < LIMIT - 1; i++) {
            assertThat(names(nextTurn(member, id))).containsExactly("delta", "turn");
        }
        JsonNode before = debateJson(member, id);
        assertThat(before.at("/data/status").asText()).isEqualTo("ACTIVE");
        assertThat(before.at("/data/remainingTurns").asInt()).isEqualTo(1);

        List<Sse> last = nextTurn(member, id);
        assertThat(names(last)).containsExactly("delta", "turn");
        assertThat(last.get(1).data().get("order").asInt()).isEqualTo(LIMIT);

        JsonNode after = debateJson(member, id);
        assertThat(after.at("/data/status").asText()).isEqualTo("ENDED");
        assertThat(after.at("/data/summary").asText()).isEqualTo("쟁점 요약");
        assertThat(after.at("/data/turnCount").asInt()).isEqualTo(LIMIT);
        assertThat(after.at("/data/remainingTurns").asInt()).isZero();

        turnRequest(member, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEBATE_ENDED"));
        postMessage(member, id, "13번째").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEBATE_ENDED"));
        endDebate(member, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEBATE_ENDED"));
        assertThat(storedTurnCount(id)).isEqualTo(LIMIT);
    }

    @Test
    void twelfthUserMessageEndsTheDebateAndStoresSummary() throws Exception {
        MockHttpSession member = newMember();
        String id = newDebate(member, persona(), persona());
        stubSummary("사용자 마지막 요약");

        for (int i = 0; i < LIMIT - 1; i++) {
            postMessage(member, id, "메시지 " + i).andExpect(status().isCreated());
        }
        postMessage(member, id, "마지막 메시지")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.order").value(LIMIT));

        JsonNode after = debateJson(member, id);
        assertThat(after.at("/data/status").asText()).isEqualTo("ENDED");
        assertThat(after.at("/data/summary").asText()).isEqualTo("사용자 마지막 요약");
        assertThat(after.at("/data/turnCount").asInt()).isEqualTo(LIMIT);

        postMessage(member, id, "더 할 말").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEBATE_ENDED"));
    }

    @Test
    void summaryFailureAtTwelfthTurnKeepsTheTurnAndTheDebateActive() throws Exception {
        MockHttpSession member = newMember();
        String id = newDebate(member, persona(), persona());
        stubStream("발언");
        doThrow(new ApiException(ErrorCode.AI_FAILED)).when(aiClient).complete(anyList(), anyBoolean());

        for (int i = 0; i < LIMIT - 1; i++) {
            nextTurn(member, id);
        }
        List<Sse> last = nextTurn(member, id);
        assertThat(names(last)).containsExactly("delta", "turn", "error");
        assertThat(errorCode(last)).isEqualTo("AI_FAILED");

        JsonNode stuck = debateJson(member, id);
        assertThat(stuck.at("/data/status").asText()).isEqualTo("ACTIVE");
        assertThat(stuck.at("/data/turnCount").asInt()).isEqualTo(LIMIT);
        assertThat(stuck.at("/data/summary").isNull()).isTrue();

        turnRequest(member, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TURN_LIMIT_REACHED"));
        postMessage(member, id, "아직 가능?").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TURN_LIMIT_REACHED"));

        stubSummary("재시도 요약");
        endDebate(member, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED"))
                .andExpect(jsonPath("$.data.summary").value("재시도 요약"));
    }

    @Test
    void summaryFailureAfterTwelfthUserMessageStillReturnsTheSavedTurn() throws Exception {
        MockHttpSession member = newMember();
        String id = newDebate(member, persona(), persona());
        doThrow(new ApiException(ErrorCode.AI_FAILED)).when(aiClient).complete(anyList(), anyBoolean());

        for (int i = 0; i < LIMIT - 1; i++) {
            postMessage(member, id, "메시지 " + i).andExpect(status().isCreated());
        }
        postMessage(member, id, "열두 번째").andExpect(status().isCreated());

        JsonNode stuck = debateJson(member, id);
        assertThat(stuck.at("/data/status").asText()).isEqualTo("ACTIVE");
        assertThat(stuck.at("/data/turnCount").asInt()).isEqualTo(LIMIT);
    }

    @Test
    void whileAnAiTurnStreamsAnotherTurnMessageOrEndGets409() throws Exception {
        MockHttpSession member = newMember();
        String id = newDebate(member, persona(), persona());
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(inv -> {
            awaitStreamStart();
            Consumer<String> onDelta = inv.getArgument(1);
            onDelta.accept("생각 중");
            started.countDown();
            release.await(10, TimeUnit.SECONDS);
            return "기다린 발언";
        }).when(aiClient).stream(anyList(), any());

        MvcResult running = startTurn(member, id);
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();

        turnRequest(member, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REQUEST_IN_PROGRESS"));
        postMessage(member, id, "끼어들기").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REQUEST_IN_PROGRESS"));
        endDebate(member, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REQUEST_IN_PROGRESS"));

        release.countDown();
        List<Sse> events = finishTurn(running);
        assertThat(names(events)).containsExactly("delta", "turn");
        assertThat(storedTurnCount(id)).isEqualTo(1);

        // the lock is released once the stream is done
        postMessage(member, id, "이제 가능").andExpect(status().isCreated());
        assertThat(storedTurnCount(id)).isEqualTo(2);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<List<AiMessage>> newPromptCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    private static String errorCode(List<Sse> events) {
        Sse error = events.stream().filter(e -> e.event().equals("error")).findFirst().orElseThrow();
        return error.data().get("code").asText();
    }
}
