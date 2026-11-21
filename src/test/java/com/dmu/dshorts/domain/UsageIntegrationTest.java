package com.dmu.dshorts.domain;

import com.dmu.dshorts.domain.debate.service.DebateClock;
import com.dmu.dshorts.domain.user.entity.UserRole;
import com.dmu.dshorts.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;

class UsageIntegrationTest extends IntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @MockitoBean DebateClock clock;

    @Test
    void usageAggregatesArePrivateAtomicAndResetAtKstMidnight() throws Exception {
        when(clock.now()).thenReturn(Instant.parse("2026-11-17T14:59:59Z"));
        var user=createUser("usage","usage-password",UserRole.USER);
        var session=login(user.getUsername(),"usage-password");
        String postId=mockMvc.perform(get("/api/v1/feed")).andReturn().getResponse().getContentAsString();
        JsonNode post=objectMapper.readTree(postId).path("data").path("items").get(0);
        String id=post.path("id").asText();
        int seconds=post.path("estimatedReadSeconds").asInt();
        mockMvc.perform(put("/api/v1/usage/today/pledge").with(withSession(session)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("pledgedCount",1)))).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/usage/today/pledge").with(withSession(session)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("pledgedCount",2)))).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("PLEDGE_ALREADY_SET"));
        mockMvc.perform(post("/api/v1/usage/bundles").with(withSession(session)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("postIds",List.of(id))))).andExpect(status().isOk()).andExpect(jsonPath("$.data.bundle.estimatedSeconds").value(seconds)).andExpect(jsonPath("$.data.stats.today.count").value(1)).andExpect(jsonPath("$.data.pledge.exceeded").value(false));
        mockMvc.perform(post("/api/v1/usage/bundles").with(withSession(session)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("postIds",List.of(id))))).andExpect(status().isOk()).andExpect(jsonPath("$.data.stats.today.count").value(2)).andExpect(jsonPath("$.data.pledge.exceeded").value(true));
        mockMvc.perform(post("/api/v1/usage/stops").with(withSession(session))).andExpect(status().isOk()).andExpect(jsonPath("$.data.stopsThisWeek").value(1));
        int before=jdbc.queryForObject("SELECT count(*) FROM daily_usage",Integer.class);
        mockMvc.perform(post("/api/v1/usage/bundles").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("postIds",List.of(id))))).andExpect(status().isOk()).andExpect(jsonPath("$.data.stats").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_usage",Integer.class)).isEqualTo(before);
        mockMvc.perform(post("/api/v1/usage/bundles").with(withSession(session)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("postIds",List.of(id,id))))).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/usage/bundles").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("postIds",List.of("999999999"))))).andExpect(status().isBadRequest());
        when(clock.now()).thenReturn(Instant.parse("2026-11-17T15:00:00Z"));
        mockMvc.perform(get("/api/v1/usage/today").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.date").value("2026-11-18")).andExpect(jsonPath("$.data.needsPledge").value(true)).andExpect(jsonPath("$.data.today.count").value(0)).andExpect(jsonPath("$.data.week.count").value(2));
        mockMvc.perform(get("/api/v1/usage/today")).andExpect(status().isUnauthorized());
    }

    @Test
    void booksRequireAdminSupportPartialEditsAndTipsOnlyOnce() throws Exception {
        when(clock.now()).thenReturn(Instant.parse("2026-11-19T01:00:00Z"));
        var admin=loginAsAdmin();
        var user=createUser("bookusage","usage-password",UserRole.USER);
        var session=login(user.getUsername(),"usage-password");
        mockMvc.perform(get("/api/v1/admin/reading-books").session(session)).andExpect(status().isForbidden());
        String response=mockMvc.perform(post("/api/v1/admin/reading-books").with(withSession(admin)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("title","시험 도서","author","저자","readingMinutes",30)))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String bookId=objectMapper.readTree(response).path("data").path("id").asText();
        mockMvc.perform(patch("/api/v1/admin/reading-books/"+bookId).with(withSession(admin)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("readingMinutes",40)))).andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("시험 도서")).andExpect(jsonPath("$.data.readingMinutes").value(40));
        JsonNode post=objectMapper.readTree(mockMvc.perform(get("/api/v1/feed")).andReturn().getResponse().getContentAsString()).path("data").path("items").get(0);
        String body=json(Map.of("postIds",List.of(post.path("id").asText())));
        mockMvc.perform(post("/api/v1/usage/bundles").with(withSession(session)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.tip.title").value("시험 도서"));
        mockMvc.perform(post("/api/v1/usage/bundles").with(withSession(session)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.tip").doesNotExist());
        mockMvc.perform(delete("/api/v1/admin/reading-books/"+bookId).with(withSession(admin))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM reading_books WHERE id=?",Boolean.class,Long.valueOf(bookId))).isTrue();
    }
    @Test
    void concurrentBundlesIncrementWithoutLostUpdatesAndOnlyOneTip() throws Exception {
        when(clock.now()).thenReturn(Instant.parse("2026-11-20T01:00:00Z"));
        var user = createUser("parallelusage", "usage-password", UserRole.USER);
        var session = login(user.getUsername(), "usage-password");
        var feed = objectMapper.readTree(mockMvc.perform(get("/api/v1/feed")).andReturn().getResponse().getContentAsString()).path("data").path("items");
        String body = json(Map.of("postIds", List.of(feed.get(0).path("id").asText())));
        Long book = jdbc.queryForObject("INSERT INTO reading_books(title,author,reading_minutes) VALUES ('동시성 도서','저자',30) RETURNING id", Long.class);
        try {
            var start = new java.util.concurrent.CountDownLatch(1);
            var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
            try {
                var calls = java.util.stream.IntStream.range(0, 6).mapToObj(i -> pool.submit(() -> {
                    start.await();
                    return objectMapper.readTree(mockMvc.perform(post("/api/v1/usage/bundles").with(withSession(session))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()).path("data");
                })).toList();
                start.countDown();
                int tips = 0;
                for (var call : calls) if (!call.get(15, java.util.concurrent.TimeUnit.SECONDS).path("tip").isNull()) tips++;
                assertThat(tips).isEqualTo(1);
                mockMvc.perform(get("/api/v1/usage/today").session(session)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.today.count").value(6));
            } finally { pool.shutdownNow(); }
        } finally { jdbc.update("UPDATE reading_books SET deleted_at=now() WHERE id=?", book); }
    }

    @Test
    void hiddenPostsRejectedAndBundleEstimateSumsIndividualPostRounding() throws Exception {
        when(clock.now()).thenReturn(Instant.parse("2026-11-21T01:00:00Z"));
        var feed = objectMapper.readTree(mockMvc.perform(get("/api/v1/feed")).andReturn().getResponse().getContentAsString()).path("data").path("items");
        String first = feed.get(0).path("id").asText();
        String second = feed.get(1).path("id").asText();
        int expected = feed.get(0).path("estimatedReadSeconds").asInt() + feed.get(1).path("estimatedReadSeconds").asInt();
        mockMvc.perform(post("/api/v1/usage/bundles").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("postIds", List.of(first, second))))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.bundle.estimatedSeconds").value(expected));
        jdbc.update("UPDATE posts SET status='HIDDEN' WHERE id=?", Long.valueOf(first));
        try {
            mockMvc.perform(post("/api/v1/usage/bundles").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("postIds", List.of(first))))).andExpect(status().isBadRequest());
        } finally { jdbc.update("UPDATE posts SET status='PUBLISHED' WHERE id=?", Long.valueOf(first)); }
    }

    @Test
    void bookMatchesBundlePersonaBeforeGeneralFallbackAndCanClearPersona() throws Exception {
        when(clock.now()).thenReturn(Instant.parse("2026-11-22T01:00:00Z"));
        var feed = objectMapper.readTree(mockMvc.perform(get("/api/v1/feed")).andReturn().getResponse().getContentAsString()).path("data").path("items");
        String id = feed.get(0).path("id").asText();
        Long persona = Long.valueOf(feed.get(0).path("persona").path("id").asText());
        Long general = jdbc.queryForObject("INSERT INTO reading_books(title,author,reading_minutes) VALUES ('일반 도서','저자',50) RETURNING id", Long.class);
        Long matched = jdbc.queryForObject("INSERT INTO reading_books(persona_id,title,author,reading_minutes) VALUES (?,'연결 도서','저자',20) RETURNING id", Long.class, persona);
        String body = json(Map.of("postIds", List.of(id)));
        try {
            mockMvc.perform(post("/api/v1/usage/bundles").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.tip.id").value(matched.toString()));
            mockMvc.perform(patch("/api/v1/admin/reading-books/" + matched).with(withSession(loginAsAdmin()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"personaId\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.personaId").doesNotExist());
            mockMvc.perform(post("/api/v1/usage/bundles").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.tip.id").value(general.toString()));
        } finally { jdbc.update("UPDATE reading_books SET deleted_at=now() WHERE id IN (?,?)", general, matched); }
    }

}
