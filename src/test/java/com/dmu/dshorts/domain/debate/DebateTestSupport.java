package com.dmu.dshorts.domain.debate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dmu.dshorts.domain.debate.repository.DebateRepository;
import com.dmu.dshorts.domain.debate.repository.DebateTurnRepository;
import com.dmu.dshorts.domain.debate.service.DebateClock;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.user.entity.User;
import com.dmu.dshorts.domain.user.entity.UserRole;
import com.dmu.dshorts.global.ai.AiClient;
import com.dmu.dshorts.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Shared fixtures for the debate tests: mocked AI and clock, personas, members and SSE parsing. */
abstract class DebateTestSupport extends IntegrationTestSupport {

    protected static final String PASSWORD = "debate-test-pw";
    private static final AtomicInteger PERSONA_SEQ = new AtomicInteger();

    @MockitoBean
    protected AiClient aiClient;

    @MockitoBean
    protected DebateClock debateClock;

    @Autowired
    protected PersonaRepository personaRepository;

    @Autowired
    protected DebateRepository debateRepository;

    @Autowired
    protected DebateTurnRepository turnRepository;

    protected final AtomicReference<Instant> now = new AtomicReference<>();

    /** One SSE event as written by the controller. */
    protected record Sse(String event, JsonNode data) {
    }

    @BeforeEach
    void setUpDebateFixtures() {
        now.set(Instant.parse("2026-11-17T03:00:00Z")); // 12:00 KST
        when(debateClock.now()).thenAnswer(inv -> now.get());
        when(aiClient.configured()).thenReturn(true);
        when(aiClient.modelName()).thenReturn("test-model");
    }

    protected Persona persona() {
        Persona p = new Persona("dbt.p" + System.nanoTime() % 100_000 + "." + PERSONA_SEQ.incrementAndGet(),
                "대담 테스트 인물");
        p.setBio("소개");
        p.setPerspective("관점");
        p.setVoice("말투");
        return personaRepository.save(p);
    }

    protected MockHttpSession newMember() throws Exception {
        User user = createUser("dbt", PASSWORD, UserRole.USER);
        return login(user.getUsername(), PASSWORD);
    }

    protected Long userIdOf(MockHttpSession session) throws Exception {
        MvcResult me = mockMvc.perform(get("/api/v1/users/me").session(session)).andReturn();
        return Long.valueOf(readJson(me).at("/data/id").asText());
    }

    protected ResultActions createDebate(MockHttpSession session, Object first, Object second, String topic)
            throws Exception {
        return mockMvc.perform(post("/api/v1/debates").with(withSession(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("personaIds", List.of(String.valueOf(first), String.valueOf(second)),
                        "topic", topic))));
    }

    /** Creates a debate and returns its id (asserts 201). */
    protected String newDebate(MockHttpSession session, Persona a, Persona b) throws Exception {
        MvcResult result = createDebate(session, a.getId(), b.getId(), "논제 " + System.nanoTime() % 10_000)
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).at("/data/id").asText();
    }

    protected ResultActions postMessage(MockHttpSession session, String id, String content) throws Exception {
        return mockMvc.perform(post("/api/v1/debates/{id}/messages", id).with(withSession(session))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("content", content))));
    }

    protected ResultActions endDebate(MockHttpSession session, String id) throws Exception {
        return mockMvc.perform(post("/api/v1/debates/{id}/end", id).with(withSession(session)));
    }

    /** Plain POST /turns: a JSON error response (no stream started). */
    protected ResultActions turnRequest(MockHttpSession session, String id) throws Exception {
        return mockMvc.perform(post("/api/v1/debates/{id}/turns", id).with(withSession(session)));
    }

    /**
     * POST /turns that must start the SSE stream; returns the async result to dispatch later. The AI stub of
     * the turn waits for a permit that is released only after this request has returned, so the worker thread
     * never writes to the response while MockMvc is still finishing the request.
     */
    protected MvcResult startTurn(MockHttpSession session, String id) throws Exception {
        MvcResult started = mockMvc.perform(post("/api/v1/debates/{id}/turns", id).with(withSession(session)))
                .andExpect(request().asyncStarted())
                .andReturn();
        streamGate.release();
        return started;
    }

    /** Permits released by {@link #startTurn}; each streamed AI call takes one before it emits anything. */
    protected final Semaphore streamGate = new Semaphore(0);

    protected void awaitStreamStart() throws InterruptedException {
        if (!streamGate.tryAcquire(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("stream was not started by startTurn");
        }
    }

    /** Completes a started stream and returns its events. */
    protected List<Sse> finishTurn(MvcResult started) throws Exception {
        MvcResult dispatched = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn();
        return parseEvents(dispatched.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Starts and completes one AI turn. */
    protected List<Sse> nextTurn(MockHttpSession session, String id) throws Exception {
        return finishTurn(startTurn(session, id));
    }

    protected List<Sse> parseEvents(String body) throws Exception {
        List<Sse> events = new ArrayList<>();
        for (String block : body.split("\n\n")) {
            String name = null;
            String data = null;
            for (String line : block.split("\n")) {
                if (line.startsWith("event:")) {
                    name = line.substring("event:".length()).strip();
                } else if (line.startsWith("data:")) {
                    data = line.substring("data:".length()).strip();
                }
            }
            if (name != null && data != null) {
                events.add(new Sse(name, objectMapper.readTree(data)));
            }
        }
        return events;
    }

    protected static List<String> names(List<Sse> events) {
        return events.stream().map(Sse::event).toList();
    }

    protected JsonNode debateJson(MockHttpSession session, String id) throws Exception {
        return readJson(mockMvc.perform(get("/api/v1/debates/{id}", id).session(session))
                .andExpect(status().isOk())
                .andReturn());
    }

    /** AI stream that sends the given fragments as deltas and returns their concatenation. */
    protected void stubStream(String... fragments) throws Exception {
        doAnswer(inv -> {
            awaitStreamStart();
            Consumer<String> onDelta = inv.getArgument(1);
            StringBuilder all = new StringBuilder();
            for (String fragment : fragments) {
                onDelta.accept(fragment);
                all.append(fragment);
            }
            return all.toString();
        }).when(aiClient).stream(anyList(), any());
    }

    /** AI stream that fails with the given exception once started. */
    protected void stubStreamFailure(RuntimeException failure) throws Exception {
        doAnswer(inv -> {
            awaitStreamStart();
            throw failure;
        }).when(aiClient).stream(anyList(), any());
    }

    protected void stubSummary(String text) {
        doReturn(text).when(aiClient).complete(anyList(), anyBoolean());
    }

    protected int storedTurnCount(String id) {
        return debateRepository.findById(Long.valueOf(id)).orElseThrow().getTurnCount();
    }

    protected long storedTurnRows(String id) {
        return turnRepository.findByDebateIdOrderByTurnOrderAsc(Long.valueOf(id)).size();
    }
}
