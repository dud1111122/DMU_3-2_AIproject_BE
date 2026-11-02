package com.dmu.dshorts.domain.persona;

import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.post.ReadSideTestSupport;
import com.dmu.dshorts.domain.user.entity.User;
import com.dmu.dshorts.domain.user.entity.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Persona list, recommendations and profile. Transactional: fixtures roll back (see PostReadIntegrationTest). */
@Transactional
class PersonaReadIntegrationTest extends ReadSideTestSupport {

    @Test
    void listIsIdAscWithCursorAndFilters() throws Exception {
        JsonNode page1 = readJson(mockMvc.perform(get("/api/v1/personas").param("limit", "3"))
                .andExpect(status().isOk()).andReturn()).get("data");
        List<String> firstIds = ids(page1.get("items"));
        assertThat(firstIds).hasSize(3);
        assertThat(firstIds.stream().map(Long::valueOf).toList()).isSorted();
        assertThat(page1.get("items").get(0).get("topics").isArray()).isTrue();
        assertThat(page1.get("items").get(0).has("voice")).isFalse();

        JsonNode page2 = readJson(mockMvc.perform(get("/api/v1/personas").param("limit", "3")
                        .param("cursor", page1.get("nextCursor").asText()))
                .andExpect(status().isOk()).andReturn()).get("data");
        assertThat(ids(page2.get("items"))).doesNotContainAnyElementsOf(firstIds);
        assertThat(Long.valueOf(ids(page2.get("items")).get(0))).isGreaterThan(Long.valueOf(firstIds.get(2)));

        JsonNode byName = readJson(mockMvc.perform(get("/api/v1/personas").param("q", "퀴리"))
                .andExpect(status().isOk()).andReturn()).at("/data/items");
        assertThat(byName).hasSize(1);
        assertThat(byName.get(0).get("username").asText()).isEqualTo("marie.curie");

        JsonNode byTopic = readJson(mockMvc.perform(get("/api/v1/personas")
                        .param("topicId", topic("경제").getId().toString()))
                .andExpect(status().isOk()).andReturn()).at("/data/items");
        List<String> usernames = new ArrayList<>();
        byTopic.forEach(item -> usernames.add(item.get("username").asText()));
        assertThat(usernames).contains("adam.smith").doesNotContain("nietzsche");
    }

    @Test
    void recommendationsRankByOverlapThenIdAndFallBackToIdOrder() throws Exception {
        String csv = topic("철학").getId() + "," + topic("심리").getId();
        JsonNode items = readJson(mockMvc.perform(get("/api/v1/personas/recommendations").param("topicIds", csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextCursor").isEmpty())
                .andReturn()).at("/data/items");
        assertThat(items.size()).isBetween(1, 10);
        assertThat(items.get(0).get("username").asText()).isEqualTo("socrates");
        assertThat(items.get(0).get("matchedTopicCount").asInt()).isEqualTo(2);
        assertThat(items.get(1).get("username").asText()).isEqualTo("nietzsche");
        assertThat(items.get(1).get("matchedTopicCount").asInt()).isEqualTo(2);
        assertThat(items.get(2).get("username").asText()).isEqualTo("kant");
        assertThat(items.get(2).get("matchedTopicCount").asInt()).isEqualTo(1);
        List<Integer> counts = new ArrayList<>();
        items.forEach(item -> counts.add(item.get("matchedTopicCount").asInt()));
        assertThat(counts).isSortedAccordingTo((a, b) -> b - a);

        JsonNode guestDefault = readJson(mockMvc.perform(get("/api/v1/personas/recommendations"))
                .andExpect(status().isOk()).andReturn()).at("/data/items");
        assertThat(guestDefault.get(0).get("username").asText()).isEqualTo("socrates");
        assertThat(guestDefault.get(0).get("matchedTopicCount").asInt()).isZero();
        assertThat(ids(guestDefault).stream().map(Long::valueOf).toList()).isSorted();

        mockMvc.perform(get("/api/v1/personas/recommendations").param("topicIds", "1,x"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/personas/recommendations").param("topicIds", "1,2,3,4,5,6,7,8,9,10,11"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void memberRecommendationsUseStoredInterestsWhenTopicIdsOmitted() throws Exception {
        User user = createUser("reco", "pw-reco", UserRole.USER);
        MockHttpSession session = login(user.getUsername(), "pw-reco");
        mockMvc.perform(put("/api/v1/users/me/interests/{id}", topic("경제").getId()).with(withSession(session)))
                .andExpect(status().isNoContent());

        JsonNode items = readJson(mockMvc.perform(get("/api/v1/personas/recommendations").session(session))
                .andExpect(status().isOk()).andReturn()).at("/data/items");
        assertThat(items.get(0).get("username").asText()).isEqualTo("adam.smith");
        assertThat(items.get(0).get("matchedTopicCount").asInt()).isEqualTo(1);
        assertThat(items.get(1).get("matchedTopicCount").asInt()).isZero();

        // explicit topicIds win over stored interests
        JsonNode explicit = readJson(mockMvc.perform(get("/api/v1/personas/recommendations").session(session)
                        .param("topicIds", topic("예술").getId().toString()))
                .andExpect(status().isOk()).andReturn()).at("/data/items");
        assertThat(explicit.get(0).get("username").asText()).isEqualTo("leonardo.davinci");
    }

    @Test
    void detailExposesPublicProfileAndFollowState() throws Exception {
        Persona einstein = persona("einstein");
        JsonNode data = readJson(mockMvc.perform(get("/api/v1/personas/{id}", einstein.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("einstein"))
                .andExpect(jsonPath("$.data.aiPersona").value(true))
                .andExpect(jsonPath("$.data.authenticated").value(false))
                .andExpect(jsonPath("$.data.followed").value(false))
                .andReturn()).get("data");
        assertThat(data.get("aiDisclosure").asText()).isNotBlank();
        List<String> topics = new ArrayList<>();
        data.get("topics").forEach(topic -> topics.add(topic.get("name").asText()));
        assertThat(topics).containsExactly("철학", "과학");
        assertThat(data.get("postCount").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(data.has("perspective") || data.has("voice") || data.has("knowledgeScope")
                || data.has("prohibitions") || data.has("conversationRules")).isFalse();

        User user = createUser("pdetail", "pw-pd", UserRole.USER);
        MockHttpSession session = login(user.getUsername(), "pw-pd");
        mockMvc.perform(put("/api/v1/users/me/follows/{id}", einstein.getId()).with(withSession(session)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/personas/{id}", einstein.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true))
                .andExpect(jsonPath("$.data.followed").value(true));

        mockMvc.perform(get("/api/v1/personas/{id}", 999_999_999L)).andExpect(status().isNotFound());
    }
}
