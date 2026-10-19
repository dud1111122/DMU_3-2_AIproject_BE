package com.dmu.dshorts.global;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dmu.dshorts.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ErrorHandlingIntegrationTest extends IntegrationTestSupport {

    @Test
    void nonNumericPathIdIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/test-probe/items/abc"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.fields").isArray());
    }

    @Test
    void numericPathIdIsAccepted() throws Exception {
        mockMvc.perform(get("/api/v1/test-probe/items/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(42));
    }

    @Test
    void missingParameterIsValidationFailed() throws Exception {
        mockMvc.perform(get("/api/v1/test-probe/required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields[0].field").value("q"));
    }

    @Test
    void invalidBodyIsValidationFailedAndBrokenJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/test-probe/body").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields[0].field").value("name"));

        mockMvc.perform(post("/api/v1/test-probe/body").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void apiExceptionDetailsAreRendered() throws Exception {
        mockMvc.perform(get("/api/v1/test-probe/limit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("DAILY_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.error.details.remainingToday").value(0));
    }

    @Test
    void unknownUrlIsNotFoundAndUnexpectedErrorIs500() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        mockMvc.perform(get("/api/v1/test-probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }
}
