package com.dmu.dshorts.domain.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dmu.dshorts.domain.generation.entity.GenerationJob;
import com.dmu.dshorts.domain.generation.entity.GenerationJobStatus;
import com.dmu.dshorts.domain.generation.repository.GenerationJobRepository;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.topic.repository.TopicRepository;
import com.dmu.dshorts.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/** GET /api/v1/admin/generation-jobs: admin 200, demo user 403, guest 401, filters and keyset cursor. */
class AdminGenerationJobEndpointTest extends IntegrationTestSupport {

    private static final String PATH = "/api/v1/admin/generation-jobs";

    @Autowired
    GenerationJobRepository jobRepository;
    @Autowired
    PersonaRepository personaRepository;
    @Autowired
    TopicRepository topicRepository;

    private void failedJobs(int count) {
        Persona persona = personaRepository.findByUsername("socrates").orElseThrow();
        Long topicId = topicRepository.findByName("철학").orElseThrow().getId();
        String runKey = "adm-" + System.nanoTime();
        for (int i = 1; i <= count; i++) {
            GenerationJob job = new GenerationJob(runKey + ":" + persona.getId() + ":" + i, persona.getId(),
                    topicId, "관리자 목록 테스트 " + i);
            job.setStatus(GenerationJobStatus.FAILED);
            job.setErrorMessage("테스트 오류");
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
        }
    }

    @Test
    void guestIsUnauthorizedAndDemoUserIsForbidden() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH).session(loginAsDemo())).andExpect(status().isForbidden());
    }

    @Test
    void adminSeesJobsWithStatusAndErrorAndPagesWithCursor() throws Exception {
        failedJobs(2);
        MockHttpSession admin = loginAsAdmin();

        MvcResult first = mockMvc.perform(get(PATH).param("status", "FAILED").param("limit", "1").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.items[0].errorMessage").value("테스트 오류"))
                .andExpect(jsonPath("$.data.items[0].resultPostId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].personaUsername").value("socrates"))
                .andExpect(jsonPath("$.data.items[0].topicName").value("철학"))
                .andReturn();
        JsonNode firstPage = readJson(first);
        String cursor = firstPage.path("data").path("nextCursor").asText();
        assertThat(cursor).isNotBlank();
        String firstId = firstPage.path("data").path("items").get(0).path("id").asText();

        MvcResult second = mockMvc.perform(get(PATH).param("status", "FAILED").param("limit", "1")
                        .param("cursor", cursor).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value("FAILED"))
                .andReturn();
        assertThat(readJson(second).path("data").path("items").get(0).path("id").asText()).isNotEqualTo(firstId);
    }

    @Test
    void invalidStatusFilterAndCursorAreValidationErrors() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        mockMvc.perform(get(PATH).param("status", "BOGUS").session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get(PATH).param("cursor", "%%%").session(admin))
                .andExpect(status().isBadRequest());
    }
}
