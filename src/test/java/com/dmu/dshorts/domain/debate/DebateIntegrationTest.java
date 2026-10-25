package com.dmu.dshorts.domain.debate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dmu.dshorts.domain.debate.repository.DebateRepository;
import com.dmu.dshorts.domain.debate.service.DebateClock;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.user.entity.User;
import com.dmu.dshorts.domain.user.entity.UserRole;
import com.dmu.dshorts.global.ai.AiClient;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.error.ErrorCode;
import com.dmu.dshorts.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class DebateIntegrationTest extends IntegrationTestSupport {

    private static final String PASSWORD = "debate-test-pw";
    private static final AtomicInteger PERSONA_SEQ = new AtomicInteger();

    @MockitoBean
    AiClient aiClient;

    @MockitoBean
    DebateClock debateClock;

    @Autowired
    PersonaRepository personaRepository;

    @Autowired
    DebateRepository debateRepository;

    private final AtomicReference<Instant> now = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        now.set(Instant.now());
        when(debateClock.now()).thenAnswer(inv -> now.get());
        when(aiClient.modelName()).thenReturn("test-model");
        when(aiClient.configured()).thenReturn(true);
    }

    // ---------------------------------------------------------------- helpers

    private Persona persona() {
        Persona p = new Persona("dbt.p" + System.nanoTime() % 100_000 + "." + PERSONA_SEQ.incrementAndGet(),
                "대담 테스트 인물");
        p.setBio("소개");
        p.setPerspective("관점");
        return personaRepository.save(p);
    }

    private MockHttpSession newMember() throws Exception {
        User user = createUser("dbt", PASSWORD, UserRole.USER);
        return login(user.getUsername(), PASSWORD);
    }

    private Long userIdOf(MockHttpSession session) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/v1/users/me").session(session)).andReturn();
        return Long.valueOf(readJson(me).at("/data/id").asText());
    }

    private long storedCount(Long userId) {
        return debateRepository.findAll().stream().filter(d -> d.getUserId().equals(userId)).count();
    }

    private ObjectNode turn(Long speaker, String stage, String body) {
        ObjectNode t = objectMapper.createObjectNode();
        t.put("personaId", String.valueOf(speaker));
        t.put("stage", stage);
        t.put("body", body);
        return t;
    }

    private ObjectNode aiOutput(Long a, Long b) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode turns = root.putArray("turns");
        for (String stage : List.of("OPENING", "REBUTTAL", "CLOSING")) {
            turns.add(turn(a, stage, "A의 " + stage + " 발언"));
            turns.add(turn(b, stage, "B의 " + stage + " 발언"));
        }
        root.put("summary", "두 사람의 쟁점 요약");
        return root;
    }

    private void stubAi(Persona a, Persona b) {
        when(aiClient.completeJson(anyList())).thenReturn(aiOutput(a.getId(), b.getId()));
    }

    private ResultActions createDebate(MockHttpSession session, Object first, Object second, String topic)
            throws Exception {
        return mockMvc.perform(post("/api/v1/debates").with(withSession(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("personaIds", List.of(String.valueOf(first), String.valueOf(second)),
                        "topic", topic))));
    }

    // ---------------------------------------------------------------- tests

    @Test
    void createsThenReusesWithSwappedOrderAndNormalizedTopic() throws Exception {
        MockHttpSession member = newMember();
        Persona a = persona();
        Persona b = persona();
        stubAi(a, b);

        MvcResult created = createDebate(member, a.getId(), b.getId(), "  Where  does   MORALITY come from? ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.persisted").value(true))
                .andExpect(jsonPath("$.data.topic").value("Where  does   MORALITY come from?"))
                .andExpect(jsonPath("$.data.turns.length()").value(6))
                .andExpect(jsonPath("$.data.turns[0].order").value(1))
                .andExpect(jsonPath("$.data.turns[0].personaId").value(String.valueOf(a.getId())))
                .andExpect(jsonPath("$.data.turns[0].stage").value("OPENING"))
                .andExpect(jsonPath("$.data.turns[5].stage").value("CLOSING"))
                .andExpect(jsonPath("$.data.turns[3].citationType").value("RECONSTRUCTION"))
                .andExpect(jsonPath("$.data.summary").value("두 사람의 쟁점 요약"))
                .andExpect(jsonPath("$.data.personas.length()").value(2))
                .andExpect(jsonPath("$.data.personas[0].perspective").doesNotExist())
                .andExpect(jsonPath("$.data.remainingToday").value(2))
                .andReturn();
        String id = readJson(created).at("/data/id").asText();

        createDebate(member, b.getId(), a.getId(), "where does morality come from?")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.remainingToday").value(2));
        verify(aiClient, times(1)).completeJson(anyList());

        mockMvc.perform(get("/api/v1/debates/{id}", id).session(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.turns.length()").value(6))
                .andExpect(jsonPath("$.data.turns[1].personaId").value(String.valueOf(b.getId())))
                .andExpect(jsonPath("$.data.turns[1].citationType").value("RECONSTRUCTION"));

        mockMvc.perform(get("/api/v1/users/me/debates").session(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(id))
                .andExpect(jsonPath("$.data.dailyLimit").value(3))
                .andExpect(jsonPath("$.data.usedToday").value(1))
                .andExpect(jsonPath("$.data.remainingToday").value(2))
                .andExpect(jsonPath("$.data.resetAt").exists());
    }

    @Test
    void fourthNewDebateInSameKstDayIs429AndResetsAtKstMidnight() throws Exception {
        MockHttpSession member = newMember();
        Persona a = persona();
        Persona b = persona();
        stubAi(a, b);
        now.set(Instant.parse("2026-11-17T14:59:00Z")); // 23:59 KST

        for (int i = 1; i <= 3; i++) {
            createDebate(member, a.getId(), b.getId(), "논제 " + i).andExpect(status().isCreated());
        }
        createDebate(member, a.getId(), b.getId(), "논제 4")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("DAILY_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.error.details.remainingToday").value(0))
                .andExpect(jsonPath("$.error.details.resetAt").value("2026-11-17T15:00:00Z"));
        verify(aiClient, times(3)).completeJson(anyList());

        // reuse still works at the limit
        createDebate(member, b.getId(), a.getId(), "논제 1").andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me/debates").param("limit", "2").session(member))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.nextCursor").isNotEmpty())
                .andExpect(jsonPath("$.data.usedToday").value(3))
                .andExpect(jsonPath("$.data.remainingToday").value(0))
                .andExpect(jsonPath("$.data.resetAt").value("2026-11-17T15:00:00Z"));

        // next KST day
        now.set(Instant.parse("2026-11-17T15:00:00Z"));
        createDebate(member, a.getId(), b.getId(), "논제 4")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.remainingToday").value(2));
    }

    @Test
    void debateIsDatedBySuccessTimeWhenAiCallCrossesKstMidnight() throws Exception {
        MockHttpSession member = newMember();
        Persona a = persona();
        Persona b = persona();
        now.set(Instant.parse("2026-11-17T14:59:59Z")); // 23:59:59 KST
        when(aiClient.completeJson(anyList())).thenAnswer(inv -> {
            now.set(Instant.parse("2026-11-17T15:00:01Z")); // 00:00:01 KST next day
            return aiOutput(a.getId(), b.getId());
        });

        createDebate(member, a.getId(), b.getId(), "자정 넘김")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.completedAt").value("2026-11-17T15:00:01Z"))
                .andExpect(jsonPath("$.data.remainingToday").value(2));

        mockMvc.perform(get("/api/v1/users/me/debates").session(member))
                .andExpect(jsonPath("$.data.usedToday").value(1))
                .andExpect(jsonPath("$.data.resetAt").value("2026-11-18T15:00:00Z"));
        now.set(Instant.parse("2026-11-17T14:59:59Z"));
        mockMvc.perform(get("/api/v1/users/me/debates").session(member))
                .andExpect(jsonPath("$.data.usedToday").value(0));
    }

    @Test
    void aiFailureConsumesNoQuotaAndSavesNothing() throws Exception {
        MockHttpSession member = newMember();
        Long userId = userIdOf(member);
        Persona a = persona();
        Persona b = persona();
        when(aiClient.completeJson(anyList())).thenThrow(new ApiException(ErrorCode.AI_FAILED));

        createDebate(member, a.getId(), b.getId(), "실패 논제")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("AI_FAILED"));

        doThrow(new ApiException(ErrorCode.AI_NOT_CONFIGURED)).when(aiClient).completeJson(anyList());
        createDebate(member, a.getId(), b.getId(), "실패 논제").andExpect(status().isServiceUnavailable());

        assertThat(storedCount(userId)).isZero();
        mockMvc.perform(get("/api/v1/users/me/debates").session(member))
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.usedToday").value(0))
                .andExpect(jsonPath("$.data.remainingToday").value(3));
    }

    @Test
    void invalidAiOutputIs502() throws Exception {
        MockHttpSession member = newMember();
        Long userId = userIdOf(member);
        Persona a = persona();
        Persona b = persona();
        Persona stranger = persona();

        List<JsonNode> invalidOutputs = new ArrayList<>();
        ObjectNode foreign = aiOutput(a.getId(), b.getId());
        ((ArrayNode) foreign.get("turns")).set(1, turn(stranger.getId(), "OPENING", "끼어든 발언"));
        invalidOutputs.add(foreign);

        ObjectNode tooLong = aiOutput(a.getId(), b.getId());
        ((ArrayNode) tooLong.get("turns")).set(2, turn(a.getId(), "REBUTTAL", "가".repeat(301)));
        invalidOutputs.add(tooLong);

        ObjectNode seven = aiOutput(a.getId(), b.getId());
        ((ArrayNode) seven.get("turns")).add(turn(a.getId(), "CLOSING", "일곱 번째"));
        invalidOutputs.add(seven);

        ObjectNode badOrder = aiOutput(a.getId(), b.getId());
        ((ArrayNode) badOrder.get("turns")).set(0, turn(a.getId(), "CLOSING", "먼저 마무리"));
        invalidOutputs.add(badOrder);

        ObjectNode duplicateStage = aiOutput(a.getId(), b.getId());
        ((ArrayNode) duplicateStage.get("turns")).set(1, turn(a.getId(), "OPENING", "또 입장"));
        invalidOutputs.add(duplicateStage);

        ObjectNode noSummary = aiOutput(a.getId(), b.getId());
        noSummary.remove("summary");
        invalidOutputs.add(noSummary);

        for (JsonNode output : invalidOutputs) {
            when(aiClient.completeJson(anyList())).thenReturn(output);
            createDebate(member, a.getId(), b.getId(), "형식 검증")
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.error.code").value("AI_FAILED"));
        }
        assertThat(storedCount(userId)).isZero();
    }

    @Test
    void inputValidation() throws Exception {
        MockHttpSession member = newMember();
        Persona a = persona();
        Persona inactive = persona();
        inactive.setActive(false);
        personaRepository.save(inactive);

        createDebate(member, a.getId(), a.getId(), "같은 사람")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value("personaIds"));
        createDebate(member, a.getId(), inactive.getId(), "비활성").andExpect(status().isNotFound());
        createDebate(member, a.getId(), 999_999_999L, "없음").andExpect(status().isNotFound());
        createDebate(member, a.getId(), "abc", "잘못된 ID").andExpect(status().isBadRequest());
        createDebate(member, a.getId(), inactive.getId(), "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field").value("topic"));
        createDebate(member, a.getId(), inactive.getId(), "가".repeat(201)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/debates").with(withSession(member)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("personaIds", List.of(String.valueOf(a.getId())), "topic", "한 명"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/debates").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("personaIds", List.of("1", "2"), "topic", "비회원"))))
                .andExpect(status().isUnauthorized());
        verify(aiClient, never()).completeJson(anyList());
    }

    @Test
    void otherUsersDebateIs404() throws Exception {
        MockHttpSession owner = newMember();
        MockHttpSession other = newMember();
        Persona a = persona();
        Persona b = persona();
        stubAi(a, b);
        String id = readJson(createDebate(owner, a.getId(), b.getId(), "소유 확인")
                .andExpect(status().isCreated()).andReturn()).at("/data/id").asText();

        mockMvc.perform(get("/api/v1/debates/{id}", id).session(other)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/debates/{id}", "999999999").session(owner)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/debates/{id}", "abc").session(owner)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/me/debates").session(other))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        // the other user's identical request is not a reuse of the owner's result
        createDebate(other, a.getId(), b.getId(), "소유 확인").andExpect(status().isCreated());
    }

    @Test
    void deactivatedPersonaHidesDebateButQuotaStillCounts() throws Exception {
        MockHttpSession member = newMember();
        Persona a = persona();
        Persona b = persona();
        stubAi(a, b);
        String id = readJson(createDebate(member, a.getId(), b.getId(), "숨김 확인")
                .andExpect(status().isCreated()).andReturn()).at("/data/id").asText();

        b.setActive(false);
        personaRepository.save(b);

        mockMvc.perform(get("/api/v1/debates/{id}", id).session(member)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/me/debates").session(member))
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.usedToday").value(1))
                .andExpect(jsonPath("$.data.remainingToday").value(2));

        b.setActive(true);
        personaRepository.save(b);
        mockMvc.perform(get("/api/v1/debates/{id}", id).session(member)).andExpect(status().isOk());
    }

    @Test
    void guestDebateWritesNothing() throws Exception {
        Persona a = persona();
        Persona b = persona();
        stubAi(a, b);
        long before = debateRepository.count();

        mockMvc.perform(post("/api/v1/guest/debates").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("personaIds", List.of(String.valueOf(b.getId()), String.valueOf(a.getId())),
                                "topic", "비회원 대담"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.persisted").value(false))
                .andExpect(jsonPath("$.data.remainingToday").doesNotExist())
                .andExpect(jsonPath("$.data.turns.length()").value(6))
                .andExpect(jsonPath("$.data.turns[0].citationType").value("RECONSTRUCTION"));
        assertThat(debateRepository.count()).isEqualTo(before);

        Persona inactive = persona();
        inactive.setActive(false);
        personaRepository.save(inactive);
        mockMvc.perform(post("/api/v1/guest/debates").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("personaIds", List.of(String.valueOf(a.getId()),
                                String.valueOf(inactive.getId())), "topic", "비회원 대담"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/guest/debates").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("personaIds", List.of(String.valueOf(a.getId()),
                                String.valueOf(a.getId())), "topic", "비회원 대담"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentRequestsFromOneUserCannotExceedLimit() throws Exception {
        MockHttpSession member = newMember();
        Long userId = userIdOf(member);
        Persona a = persona();
        Persona b = persona();
        stubAi(a, b);
        createDebate(member, a.getId(), b.getId(), "기존 1").andExpect(status().isCreated());
        createDebate(member, a.getId(), b.getId(), "기존 2").andExpect(status().isCreated());

        when(aiClient.completeJson(anyList())).thenAnswer(inv -> {
            Thread.sleep(500);
            return aiOutput(a.getId(), b.getId());
        });

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String topic = "동시 " + i;
            results.add(pool.submit(() -> {
                start.await();
                return createDebate(member, a.getId(), b.getId(), topic).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        assertThat(storedCount(userId)).isEqualTo(3);

        createDebate(member, a.getId(), b.getId(), "동시 이후").andExpect(status().isTooManyRequests());
        assertThat(storedCount(userId)).isEqualTo(3);
    }
}
