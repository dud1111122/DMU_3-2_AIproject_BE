package com.dmu.dshorts.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dmu.dshorts.domain.user.entity.User;
import com.dmu.dshorts.domain.user.entity.UserRole;
import com.dmu.dshorts.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class AuthIntegrationTest extends IntegrationTestSupport {

    @Test
    void loginReturnsProfile() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", DEMO_USERNAME, "password", DEMO_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(DEMO_USERNAME))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.interestTopicIds").isArray())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSame401() throws Exception {
        MvcResult wrongPassword = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", DEMO_USERNAME, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andReturn();
        MvcResult unknownUser = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "nobody.here", "password", "whatever"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(readJson(unknownUser).at("/error/message").asText())
                .isEqualTo(readJson(wrongPassword).at("/error/message").asText());
    }

    @Test
    void meRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        MockHttpSession demo = loginAsDemo();
        mockMvc.perform(get("/api/v1/users/me").session(demo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(DEMO_USERNAME));
    }

    @Test
    void adminPathsRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/topics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/v1/admin/topics").session(loginAsDemo()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // Admin passes the security filter; no admin endpoint exists yet, so the result is a JSON 404.
        mockMvc.perform(get("/api/v1/admin/topics").session(loginAsAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void stateChangingRequestsRequireCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", DEMO_USERNAME, "password", DEMO_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));

        MockHttpSession demo = loginAsDemo();
        mockMvc.perform(patch("/api/v1/users/me").session(demo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("bio", "no token"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void realCookieCsrfFlowWorks() throws Exception {
        MvcResult tokenResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headerName").value("X-XSRF-TOKEN"))
                .andReturn();
        JsonNode body = readJson(tokenResult);
        Cookie cookie = tokenResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isFalse();
        assertThat(cookie.getValue()).isEqualTo(body.at("/data/token").asText());

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(cookie)
                        .header("X-XSRF-TOKEN", cookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", DEMO_USERNAME, "password", DEMO_PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void updateProfileAndLogout() throws Exception {
        User user = createUser("auth.test", "password-1234", UserRole.USER);
        MockHttpSession session = login(user.getUsername(), "password-1234");

        mockMvc.perform(patch("/api/v1/users/me").with(withSession(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("displayName", "새 이름", "bio", "소개"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("새 이름"))
                .andExpect(jsonPath("$.data.bio").value("소개"))
                .andExpect(jsonPath("$.data.username").value(user.getUsername()));

        mockMvc.perform(patch("/api/v1/users/me").with(withSession(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "Bad Name!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fields[0].field").value("username"));

        mockMvc.perform(patch("/api/v1/users/me").with(withSession(session))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", DEMO_USERNAME))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));

        mockMvc.perform(post("/api/v1/auth/logout").with(withSession(session)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/users/me").session(session))
                .andExpect(status().isUnauthorized());
    }
}
