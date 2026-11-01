package com.dmu.dshorts.domain.post;

import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.post.entity.Post;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Topics, feed, search and post detail. Transactional: fixture posts are rolled back so the shared DB keeps only
 * the seed content (FoundationIntegrationTest expects every post to have key points).
 */
@Transactional
class PostReadIntegrationTest extends ReadSideTestSupport {

    @Test
    void topicsListsNonDeletedInSortOrder() throws Exception {
        JsonNode items = readJson(mockMvc.perform(get("/api/v1/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextCursor").isEmpty())
                .andReturn()).at("/data/items");
        List<Integer> sortOrders = new ArrayList<>();
        items.forEach(item -> sortOrders.add(item.get("sortOrder").asInt()));
        assertThat(sortOrders).isSorted();
        assertThat(items.get(0).get("name").asText()).isEqualTo("철학");
        assertThat(items.get(0).get("id").isTextual()).isTrue();
    }

    @Test
    void feedIsNewestFirstAndPaginatesWithCursorWithoutOverlap() throws Exception {
        JsonNode page1 = readJson(mockMvc.perform(get("/api/v1/feed").param("limit", "2"))
                .andExpect(status().isOk())
                .andReturn()).get("data");
        assertThat(page1.get("items")).hasSize(2);
        String cursor = page1.get("nextCursor").asText();
        assertThat(cursor).isNotBlank();

        JsonNode page2 = readJson(mockMvc.perform(get("/api/v1/feed").param("limit", "2").param("cursor", cursor))
                .andExpect(status().isOk())
                .andReturn()).get("data");
        assertThat(ids(page2.get("items"))).doesNotContainAnyElementsOf(ids(page1.get("items")));

        List<Instant> times = new ArrayList<>();
        page1.get("items").forEach(item -> times.add(Instant.parse(item.get("publishedAt").asText())));
        page2.get("items").forEach(item -> times.add(Instant.parse(item.get("publishedAt").asText())));
        assertThat(times).isSortedAccordingTo((a, b) -> b.compareTo(a));

        JsonNode first = page1.get("items").get(0);
        assertThat(first.get("persona").get("username").isTextual()).isTrue();
        assertThat(first.get("sections").isArray()).isTrue();
        assertThat(first.get("sections").get(0).get("order").asInt()).isEqualTo(1);
        assertThat(first.get("topics").isArray()).isTrue();
        assertThat(first.get("likeCount").isNumber()).isTrue();
        assertThat(first.get("userState").get("authenticated").asBoolean()).isFalse();
        assertThat(first.has("keyPoints")).isFalse();
    }

    @Test
    void feedRejectsMalformedCursorAndBadLimit() throws Exception {
        mockMvc.perform(get("/api/v1/feed").param("cursor", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mockMvc.perform(get("/api/v1/feed").param("limit", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/feed").param("topicId", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void feedFiltersByTopic() throws Exception {
        String scienceId = topic("과학").getId().toString();
        JsonNode items = readJson(mockMvc.perform(get("/api/v1/feed").param("topicId", scienceId).param("limit", "50"))
                .andExpect(status().isOk())
                .andReturn()).at("/data/items");
        assertThat(items.size()).isGreaterThanOrEqualTo(2);
        for (JsonNode item : items) {
            List<String> topicIds = new ArrayList<>();
            item.get("topics").forEach(topic -> topicIds.add(topic.get("id").asText()));
            assertThat(topicIds).contains(scienceId);
        }
        List<String> all = ids(items);
        assertThat(all).contains(seedPost("einstein").getId().toString(), seedPost("marie.curie").getId().toString());
        assertThat(all).doesNotContain(seedPost("nietzsche").getId().toString());
    }

    @Test
    void followingFeedRequiresLoginAndOnlyShowsFollowedPersonas() throws Exception {
        mockMvc.perform(get("/api/v1/feed").param("following", "true"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        Persona einstein = persona("einstein");
        MockHttpSession session = login(createUser("feedfollow", "pw-feed", com.dmu.dshorts.domain.user.entity.UserRole.USER)
                .getUsername(), "pw-feed");
        JsonNode empty = readJson(mockMvc.perform(get("/api/v1/feed").param("following", "true").session(session))
                .andExpect(status().isOk()).andReturn()).at("/data/items");
        assertThat(empty).isEmpty();

        mockMvc.perform(put("/api/v1/users/me/follows/{id}", einstein.getId()).with(withSession(session)))
                .andExpect(status().isNoContent());
        JsonNode items = readJson(mockMvc.perform(get("/api/v1/feed").param("following", "true").session(session))
                .andExpect(status().isOk()).andReturn()).at("/data/items");
        assertThat(items).isNotEmpty();
        for (JsonNode item : items) {
            assertThat(item.get("persona").get("id").asText()).isEqualTo(einstein.getId().toString());
            assertThat(item.get("userState").get("followingPersona").asBoolean()).isTrue();
        }
    }

    @Test
    void personaPostsListsOnlyThatPersona() throws Exception {
        Persona nietzsche = persona("nietzsche");
        JsonNode items = readJson(mockMvc.perform(get("/api/v1/personas/{id}/posts", nietzsche.getId()))
                .andExpect(status().isOk()).andReturn()).at("/data/items");
        assertThat(items).isNotEmpty();
        items.forEach(item -> assertThat(item.get("persona").get("username").asText()).isEqualTo("nietzsche"));
        mockMvc.perform(get("/api/v1/personas/{id}/posts", 999_999_999L)).andExpect(status().isNotFound());
    }

    @Test
    void searchSupportsPostPersonaAndTopicTypes() throws Exception {
        Persona persona = createPersona("과학");
        Post post = createPost(persona, "검색전용제목xq", "본문고유문자열zq", Instant.now(), "과학");

        JsonNode byTitle = readJson(mockMvc.perform(get("/api/v1/search").param("q", "전용제목XQ"))
                .andExpect(status().isOk()).andReturn()).at("/data/items");
        assertThat(ids(byTitle)).containsExactly(post.getId().toString());

        JsonNode byBody = readJson(mockMvc.perform(get("/api/v1/search").param("q", "고유문자열zq").param("type", "post"))
                .andExpect(status().isOk()).andReturn()).at("/data/items");
        assertThat(ids(byBody)).containsExactly(post.getId().toString());
        assertThat(byBody.get(0).get("sections").get(1).get("image").get("url").asText())
                .isEqualTo("/media/cards/" + post.getId() + "/2.webp");

        JsonNode personas = readJson(mockMvc.perform(get("/api/v1/search").param("q", "아인슈").param("type", "persona"))
                .andExpect(status().isOk()).andReturn()).at("/data/items");
        assertThat(personas).hasSize(1);
        assertThat(personas.get(0).get("username").asText()).isEqualTo("einstein");
        assertThat(personas.get(0).has("perspective")).isFalse();

        JsonNode topics = readJson(mockMvc.perform(get("/api/v1/search").param("q", "과").param("type", "topic"))
                .andExpect(status().isOk()).andReturn()).at("/data/items");
        assertThat(topics).hasSize(1);
        assertThat(topics.get(0).get("name").asText()).isEqualTo("과학");

        JsonNode none = readJson(mockMvc.perform(get("/api/v1/search").param("q", "존재하지않는검색어"))
                .andExpect(status().isOk()).andReturn()).at("/data/items");
        assertThat(none).isEmpty();

        mockMvc.perform(get("/api/v1/search").param("q", "a").param("type", "user")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/search").param("q", " ")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/search")).andExpect(status().isBadRequest());
    }

    @Test
    void searchIsIdDescWithCursor() throws Exception {
        Persona persona = createPersona("문학");
        Post older = createPost(persona, "커서검색kk 하나", "b", Instant.now().minusSeconds(60), "문학");
        Post newer = createPost(persona, "커서검색kk 둘", "b", Instant.now(), "문학");

        JsonNode page1 = readJson(mockMvc.perform(get("/api/v1/search").param("q", "커서검색kk").param("limit", "1"))
                .andExpect(status().isOk()).andReturn()).get("data");
        assertThat(ids(page1.get("items"))).containsExactly(newer.getId().toString());
        JsonNode page2 = readJson(mockMvc.perform(get("/api/v1/search").param("q", "커서검색kk").param("limit", "1")
                        .param("cursor", page1.get("nextCursor").asText()))
                .andExpect(status().isOk()).andReturn()).get("data");
        assertThat(ids(page2.get("items"))).containsExactly(older.getId().toString());
        assertThat(page2.get("nextCursor").isNull()).isTrue();
    }

    @Test
    void postDetailHasSpecShapeForGuestAndReflectsMemberState() throws Exception {
        Post post = seedPost("nietzsche");
        JsonNode data = readJson(mockMvc.perform(get("/api/v1/posts/{id}", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(post.getId().toString()))
                .andExpect(jsonPath("$.data.persona.username").value("nietzsche"))
                .andExpect(jsonPath("$.data.persona.aiPersona").value(true))
                .andExpect(jsonPath("$.data.groundingMode").value("MODEL_KNOWLEDGE"))
                .andExpect(jsonPath("$.data.userState.authenticated").value(false))
                .andExpect(jsonPath("$.data.userState.liked").value(false))
                .andExpect(jsonPath("$.data.userState.saved").value(false))
                .andExpect(jsonPath("$.data.userState.followingPersona").value(false))
                .andReturn()).get("data");
        assertThat(data.get("title").isTextual()).isTrue();
        assertThat(data.has("summary") && data.has("caption")).isTrue();
        assertThat(data.get("topics").get(0).get("name").asText()).isEqualTo("철학");
        assertThat(data.get("sections")).hasSize(4);
        for (int i = 0; i < 4; i++) {
            JsonNode section = data.get("sections").get(i);
            assertThat(section.get("order").asInt()).isEqualTo(i + 1);
            assertThat(section.get("body").isTextual()).isTrue();
            assertThat(section.get("citationType").isTextual()).isTrue();
            assertThat(section.has("quoteAttribution")).isTrue();
            assertThat(section.get("image").isNull()).isTrue();
        }
        assertThat(data.get("keyPoints")).hasSize(2);
        assertThat(data.get("keyPoints").get(0).get("order").asInt()).isEqualTo(1);
        assertThat(data.get("keyPoints").get(0).has("sectionId")).isTrue();
        assertThat(data.get("sources")).isNotEmpty();
        JsonNode source = data.get("sources").get(0);
        assertThat(source.get("verified").asBoolean()).isFalse();
        assertThat(source.get("checkedAt").isNull()).isTrue();
        assertThat(source.has("author") && source.has("url")).isTrue();
        assertThat(data.get("likeCount").asLong()).isGreaterThanOrEqualTo(0);

        MockHttpSession demo = loginAsDemo();
        mockMvc.perform(put("/api/v1/users/me/posts/{id}/like", post.getId()).with(withSession(demo)))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/users/me/saved-posts/{id}", post.getId()).with(withSession(demo)))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/users/me/follows/{id}", post.getPersona().getId()).with(withSession(demo)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/posts/{id}", post.getId()).session(demo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userState.authenticated").value(true))
                .andExpect(jsonPath("$.data.userState.liked").value(true))
                .andExpect(jsonPath("$.data.userState.saved").value(true))
                .andExpect(jsonPath("$.data.userState.followingPersona").value(true));

        mockMvc.perform(get("/api/v1/posts/{id}", 999_999_999L)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/posts/{id}", "abc")).andExpect(status().isNotFound());
    }
}
