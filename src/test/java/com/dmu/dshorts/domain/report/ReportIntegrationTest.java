package com.dmu.dshorts.domain.report;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.entity.PostStatus;
import com.dmu.dshorts.domain.post.repository.PostRepository;
import com.dmu.dshorts.support.IntegrationTestSupport;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** Member reports on visible posts and the admin review flow. Each test rolls back. */
@Transactional
class ReportIntegrationTest extends IntegrationTestSupport {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    PersonaRepository personaRepository;
    @Autowired
    PostRepository postRepository;

    @Test
    void memberReportIsCreatedOnceAndDuplicatesReturnExisting() throws Exception {
        MockHttpSession demo = loginAsDemo();
        Post post = newPost(PostStatus.PUBLISHED, true);
        String path = "/api/v1/posts/" + post.getId() + "/reports";

        MvcResult first = mockMvc.perform(post(path).with(withSession(demo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reasonCode", "INACCURATE", "detail", "사실 확인 필요"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.reasonCode").value("INACCURATE"))
                .andReturn();
        String reportId = readJson(first).path("data").path("id").asText();

        mockMvc.perform(post(path).with(withSession(demo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reasonCode", "FAKE_QUOTE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reportId))
                .andExpect(jsonPath("$.data.reasonCode").value("INACCURATE"));
    }

    @Test
    void reportInputAndVisibilityRules() throws Exception {
        MockHttpSession demo = loginAsDemo();
        Post visible = newPost(PostStatus.PUBLISHED, true);

        mockMvc.perform(post("/api/v1/posts/{id}/reports", visible.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reasonCode", "OTHER"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/posts/{id}/reports", visible.getId()).with(withSession(demo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reasonCode", "NOPE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/posts/{id}/reports", visible.getId()).with(withSession(demo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reasonCode", "OTHER", "detail", "가".repeat(501)))))
                .andExpect(status().isBadRequest());

        Post hidden = newPost(PostStatus.HIDDEN, true);
        mockMvc.perform(post("/api/v1/posts/{id}/reports", hidden.getId()).with(withSession(demo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reasonCode", "OTHER"))))
                .andExpect(status().isNotFound());

        Post inactivePersona = newPost(PostStatus.PUBLISHED, false);
        mockMvc.perform(post("/api/v1/posts/{id}/reports", inactivePersona.getId()).with(withSession(demo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reasonCode", "OTHER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminReviewsAndResolvesReports() throws Exception {
        MockHttpSession demo = loginAsDemo();
        MockHttpSession admin = loginAsAdmin();
        Post post = newPost(PostStatus.PUBLISHED, true);

        MvcResult created = mockMvc.perform(post("/api/v1/posts/{id}/reports", post.getId()).with(withSession(demo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reasonCode", "HARMFUL"))))
                .andExpect(status().isCreated())
                .andReturn();
        String reportId = readJson(created).path("data").path("id").asText();

        mockMvc.perform(get("/api/v1/admin/reports").param("status", "OPEN").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == '" + reportId + "')]").isNotEmpty());

        mockMvc.perform(patch("/api/v1/admin/reports/{id}", reportId).with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "OPEN"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/admin/reports/{id}", reportId).with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "RESOLVED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.resolvedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.post.id").value(String.valueOf(post.getId())))
                .andExpect(jsonPath("$.data.reporter.username").value(org.hamcrest.Matchers.is(DEMO_USERNAME)));

        mockMvc.perform(get("/api/v1/admin/reports").param("status", "OPEN").session(admin))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + reportId + "')]").isEmpty());
        mockMvc.perform(get("/api/v1/admin/reports").param("status", "RESOLVED").session(admin))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + reportId + "')]").isNotEmpty());

        mockMvc.perform(get("/api/v1/admin/reports").session(demo))
                .andExpect(status().isForbidden());
    }

    private Post newPost(PostStatus status, boolean personaActive) {
        Persona persona = new Persona("rp" + System.nanoTime() % 1_000_000_000L + SEQUENCE.incrementAndGet(), "신고 대상");
        persona.setActive(personaActive);
        persona = personaRepository.saveAndFlush(persona);
        Post post = new Post(persona, "신고 제목 " + SEQUENCE.incrementAndGet());
        post.setStatus(status);
        return postRepository.saveAndFlush(post);
    }
}
