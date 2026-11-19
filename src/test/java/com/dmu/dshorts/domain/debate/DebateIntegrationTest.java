package com.dmu.dshorts.domain.debate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/** Create, quota, members-only access, visibility, end action and list. */
class DebateIntegrationTest extends DebateTestSupport {

    @Test
    void createReturns201WithoutTurnsAndDetailAndListShowIt() throws Exception {
        MockHttpSession member = newMember();
        Persona a = persona();
        Persona b = persona();

        MvcResult created = createDebate(member, a.getId(), b.getId(), "  Where  does   MORALITY come from? ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.topic").value("Where  does   MORALITY come from?"))
                .andExpect(jsonPath("$.data.turns.length()").value(0))
                .andExpect(jsonPath("$.data.turnCount").value(0))
                .andExpect(jsonPath("$.data.turnLimit").value(30))
                .andExpect(jsonPath("$.data.remainingTurns").value(30))
                .andExpect(jsonPath("$.data.summary").doesNotExist())
                .andExpect(jsonPath("$.data.endedAt").doesNotExist())
                .andExpect(jsonPath("$.data.personas.length()").value(2))
                .andExpect(jsonPath("$.data.personas[0].id").value(String.valueOf(a.getId())))
                .andExpect(jsonPath("$.data.personas[1].id").value(String.valueOf(b.getId())))
                .andExpect(jsonPath("$.data.personas[0].perspective").doesNotExist())
                .andReturn();
        String id = readJson(created).at("/data/id").asText();

        // every POST is a new debate, even for the same pair and topic
        createDebate(member, b.getId(), a.getId(), "where does morality come from?").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/debates/{id}", id).session(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.turns.length()").value(0));
        // no AI call on create
        verify(aiClient, never()).stream(anyList(), any());
        verify(aiClient, never()).complete(anyList(), anyBoolean());

        mockMvc.perform(get("/api/v1/users/me/debates").session(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].turnCount").value(0))
                .andExpect(jsonPath("$.data.items[0].createdAt").exists());
    }

    @Test
    void createHasNoDailyLimit() throws Exception {
        MockHttpSession member = newMember();
        Persona a = persona();
        Persona b = persona();
        now.set(Instant.parse("2026-11-17T14:59:00Z")); // 23:59 KST

        for (int i = 1; i <= 5; i++) {
            createDebate(member, a.getId(), b.getId(), "논제 " + i).andExpect(status().isCreated());
        }
        mockMvc.perform(get("/api/v1/users/me/debates").param("limit", "2").session(member))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.nextCursor").isNotEmpty());
    }

    @Test
    void failedCreatesDoNotBlockLaterCreates() throws Exception {
        MockHttpSession member = newMember();
        Persona a = persona();
        Persona inactive = persona();
        inactive.setActive(false);
        personaRepository.save(inactive);

        createDebate(member, a.getId(), a.getId(), "같은 사람").andExpect(status().isBadRequest());
        createDebate(member, a.getId(), inactive.getId(), "비활성").andExpect(status().isNotFound());
        createDebate(member, a.getId(), 999_999_999L, "없음").andExpect(status().isNotFound());
        createDebate(member, a.getId(), 1L, "   ").andExpect(status().isBadRequest());

        for (int i = 1; i <= 4; i++) {
            createDebate(member, a.getId(), persona().getId(), "정상 " + i).andExpect(status().isCreated());
        }
    }

    @Test
    void inputValidation() throws Exception {
        MockHttpSession member = newMember();
        Persona a = persona();
        Persona b = persona();

        createDebate(member, a.getId(), a.getId(), "같은 사람")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value("personaIds"));
        createDebate(member, a.getId(), "abc", "잘못된 ID").andExpect(status().isBadRequest());
        createDebate(member, a.getId(), b.getId(), "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value("topic"));
        createDebate(member, a.getId(), b.getId(), "가".repeat(201)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/debates").with(withSession(member)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("personaIds", List.of(String.valueOf(a.getId())), "topic", "한 명"))))
                .andExpect(status().isBadRequest());
        createDebate(member, a.getId(), b.getId(), "가".repeat(200)).andExpect(status().isCreated());
    }

    @Test
    void membersOnlyGuestsGet401AndGuestRouteIsGone() throws Exception {
        Persona a = persona();
        Persona b = persona();
        String body = json(Map.of("personaIds", List.of(String.valueOf(a.getId()), String.valueOf(b.getId())),
                "topic", "비회원"));

        mockMvc.perform(post("/api/v1/debates").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/guest/debates").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/debates/{id}", "1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me/debates")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/debates/{id}/messages", "1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/debates/{id}/turns", "1").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/debates/{id}/end", "1").with(csrf())).andExpect(status().isUnauthorized());
        verify(aiClient, never()).stream(anyList(), any());
    }

    @Test
    void otherUsersDebateIs404EverywhereAndListIsEmpty() throws Exception {
        MockHttpSession owner = newMember();
        MockHttpSession other = newMember();
        Persona a = persona();
        Persona b = persona();
        String id = newDebate(owner, a, b);
        stubStream("첫 발언");

        mockMvc.perform(get("/api/v1/debates/{id}", id).session(other)).andExpect(status().isNotFound());
        postMessage(other, id, "끼어들기").andExpect(status().isNotFound());
        turnRequest(other, id).andExpect(status().isNotFound());
        endDebate(other, id).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/debates/{id}", "999999999").session(owner)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/debates/{id}", "abc").session(owner)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/me/debates").session(other))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        assertThat(storedTurnRows(id)).isZero();
    }

    @Test
    void hiddenPersonaHidesDebateEverywhere() throws Exception {
        MockHttpSession member = newMember();
        Persona a = persona();
        Persona b = persona();
        String id = newDebate(member, a, b);
        stubStream("발언");

        b.setActive(false);
        personaRepository.save(b);

        mockMvc.perform(get("/api/v1/debates/{id}", id).session(member)).andExpect(status().isNotFound());
        postMessage(member, id, "메시지").andExpect(status().isNotFound());
        turnRequest(member, id).andExpect(status().isNotFound());
        endDebate(member, id).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/me/debates").session(member))
                .andExpect(jsonPath("$.data.items.length()").value(0));

        b.setActive(true);
        personaRepository.save(b);
        mockMvc.perform(get("/api/v1/debates/{id}", id).session(member)).andExpect(status().isOk());
    }

    @Test
    void deletedPersonaHidesDebate() throws Exception {
        MockHttpSession member = newMember();
        Persona a = persona();
        Persona b = persona();
        String id = newDebate(member, a, b);

        b.setDeletedAt(Instant.parse("2026-11-16T00:00:00Z"));
        personaRepository.save(b);

        mockMvc.perform(get("/api/v1/debates/{id}", id).session(member)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/me/debates").session(member))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    void endRequiresATurnStoresSummaryAndIsOneWay() throws Exception {
        MockHttpSession member = newMember();
        Persona a = persona();
        Persona b = persona();
        String id = newDebate(member, a, b);

        endDebate(member, id)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
        assertThat(debateJson(member, id).at("/data/status").asText()).isEqualTo("ACTIVE");

        stubStream("첫 발언");
        stubSummary("두 사람의 쟁점 요약");
        nextTurn(member, id);

        endDebate(member, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ENDED"))
                .andExpect(jsonPath("$.data.summary").value("두 사람의 쟁점 요약"))
                .andExpect(jsonPath("$.data.endedAt").exists())
                .andExpect(jsonPath("$.data.turnCount").value(1));

        endDebate(member, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEBATE_ENDED"));
        postMessage(member, id, "이미 끝났다").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEBATE_ENDED"));
        turnRequest(member, id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DEBATE_ENDED"));

        JsonNode ended = debateJson(member, id);
        assertThat(ended.at("/data/status").asText()).isEqualTo("ENDED");
        assertThat(ended.at("/data/summary").asText()).isEqualTo("두 사람의 쟁점 요약");
        assertThat(ended.at("/data/remainingTurns").asInt()).isEqualTo(29);
        mockMvc.perform(get("/api/v1/users/me/debates").session(member))
                .andExpect(jsonPath("$.data.items[0].status").value("ENDED"))
                .andExpect(jsonPath("$.data.items[0].turnCount").value(1));
    }

    @Test
    void endAiFailureReturns502AndKeepsDebateActive() throws Exception {
        MockHttpSession member = newMember();
        Persona a = persona();
        Persona b = persona();
        String id = newDebate(member, a, b);
        stubStream("발언");
        nextTurn(member, id);

        doThrow(new ApiException(ErrorCode.AI_FAILED)).when(aiClient).complete(anyList(), anyBoolean());
        endDebate(member, id)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("AI_FAILED"));

        JsonNode after = debateJson(member, id);
        assertThat(after.at("/data/status").asText()).isEqualTo("ACTIVE");
        assertThat(after.at("/data/summary").isNull()).isTrue();
        assertThat(after.at("/data/endedAt").isNull()).isTrue();
        assertThat(after.at("/data/turnCount").asInt()).isEqualTo(1);

        stubSummary("요약");
        endDebate(member, id).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ENDED"));
    }

    @Test
    void aiNotConfiguredIsA503JsonBeforeAnyStream() throws Exception {
        MockHttpSession member = newMember();
        Persona a = persona();
        Persona b = persona();
        String id = newDebate(member, a, b);
        when(aiClient.configured()).thenReturn(false);

        turnRequest(member, id)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("AI_NOT_CONFIGURED"));
        assertThat(storedTurnRows(id)).isZero();
    }

}
