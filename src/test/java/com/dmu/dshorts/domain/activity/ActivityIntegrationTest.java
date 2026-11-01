package com.dmu.dshorts.domain.activity;

import com.dmu.dshorts.domain.activity.entity.FollowId;
import com.dmu.dshorts.domain.activity.entity.PostLikeId;
import com.dmu.dshorts.domain.activity.entity.SavedPostId;
import com.dmu.dshorts.domain.activity.repository.FollowRepository;
import com.dmu.dshorts.domain.activity.repository.PostLikeRepository;
import com.dmu.dshorts.domain.activity.repository.SavedPostRepository;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.post.ReadSideTestSupport;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.entity.PostStatus;
import com.dmu.dshorts.domain.user.entity.User;
import com.dmu.dshorts.domain.user.entity.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Interests, follows, likes, saved posts and visibility of hidden content. Transactional: all fixture rows roll back.
 */
@Transactional
class ActivityIntegrationTest extends ReadSideTestSupport {

    @Autowired
    PostLikeRepository likeRepository;
    @Autowired
    SavedPostRepository savedPostRepository;
    @Autowired
    FollowRepository followRepository;

    private MockHttpSession freshMember(String prefix) throws Exception {
        User user = createUser(prefix, "pw-" + prefix, UserRole.USER);
        return login(user.getUsername(), "pw-" + prefix);
    }

    private Long userIdOf(MockHttpSession session) throws Exception {
        return readJson(mockMvc.perform(get("/api/v1/users/me").session(session)).andReturn()).at("/data/id").asLong();
    }

    @Test
    void activityEndpointsRequireLogin() throws Exception {
        Long postId = seedPost("einstein").getId();
        mockMvc.perform(put("/api/v1/users/me/posts/{id}/like", postId).with(withSession(new MockHttpSession())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me/saved-posts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/users/me/follows")).andExpect(status().isUnauthorized());
    }

    @Test
    void interestsAreIdempotentAndShowInProfile() throws Exception {
        MockHttpSession session = freshMember("interest");
        Long topicId = topic("예술").getId();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(put("/api/v1/users/me/interests/{id}", topicId).with(withSession(session)))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(get("/api/v1/users/me").session(session))
                .andExpect(jsonPath("$.data.interestTopicIds[0]").value(topicId.toString()))
                .andExpect(jsonPath("$.data.interestTopicIds.length()").value(1));
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(delete("/api/v1/users/me/interests/{id}", topicId).with(withSession(session)))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(get("/api/v1/users/me").session(session))
                .andExpect(jsonPath("$.data.interestTopicIds.length()").value(0));
        mockMvc.perform(put("/api/v1/users/me/interests/{id}", 999_999_999L).with(withSession(session)))
                .andExpect(status().isNotFound());
    }

    @Test
    void likeIsIdempotentAndCountsRows() throws Exception {
        MockHttpSession session = freshMember("liker");
        Long userId = userIdOf(session);
        Persona persona = createPersona("과학");
        Post post = createPost(persona, "좋아요 대상", "b", Instant.now(), "과학");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(put("/api/v1/users/me/posts/{id}/like", post.getId()).with(withSession(session)))
                    .andExpect(status().isNoContent());
        }
        assertThat(likeRepository.existsById(new PostLikeId(userId, post.getId()))).isTrue();
        mockMvc.perform(get("/api/v1/posts/{id}", post.getId()).session(session))
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.userState.liked").value(true));
        mockMvc.perform(get("/api/v1/posts/{id}", post.getId()))
                .andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.userState.liked").value(false));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(delete("/api/v1/users/me/posts/{id}/like", post.getId()).with(withSession(session)))
                    .andExpect(status().isNoContent());
        }
        assertThat(likeRepository.existsById(new PostLikeId(userId, post.getId()))).isFalse();
        mockMvc.perform(get("/api/v1/posts/{id}", post.getId()).session(session))
                .andExpect(jsonPath("$.data.likeCount").value(0))
                .andExpect(jsonPath("$.data.userState.liked").value(false));
        mockMvc.perform(put("/api/v1/users/me/posts/{id}/like", 999_999_999L).with(withSession(session)))
                .andExpect(status().isNotFound());
    }

    @Test
    void followsAreIdempotentAndListedByIdAsc() throws Exception {
        MockHttpSession session = freshMember("follower");
        Long userId = userIdOf(session);
        Persona kant = persona("kant");
        Persona socrates = persona("socrates");
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(put("/api/v1/users/me/follows/{id}", kant.getId()).with(withSession(session)))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(put("/api/v1/users/me/follows/{id}", socrates.getId()).with(withSession(session)))
                .andExpect(status().isNoContent());
        assertThat(followRepository.existsById(new FollowId(userId, kant.getId()))).isTrue();

        JsonNode page1 = readJson(mockMvc.perform(get("/api/v1/users/me/follows").param("limit", "1").session(session))
                .andExpect(status().isOk()).andReturn()).get("data");
        assertThat(page1.get("items").get(0).get("username").asText()).isEqualTo("socrates");
        JsonNode page2 = readJson(mockMvc.perform(get("/api/v1/users/me/follows").param("limit", "1").session(session)
                        .param("cursor", page1.get("nextCursor").asText()))
                .andExpect(status().isOk()).andReturn()).get("data");
        assertThat(page2.get("items").get(0).get("username").asText()).isEqualTo("kant");
        assertThat(page2.get("nextCursor").isNull()).isTrue();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(delete("/api/v1/users/me/follows/{id}", kant.getId()).with(withSession(session)))
                    .andExpect(status().isNoContent());
        }
        assertThat(followRepository.existsById(new FollowId(userId, kant.getId()))).isFalse();
        mockMvc.perform(put("/api/v1/users/me/follows/{id}", 999_999_999L).with(withSession(session)))
                .andExpect(status().isNotFound());
    }

    @Test
    void savedPostsAreIdempotentListedBySaveTimeDescWithCursor() throws Exception {
        MockHttpSession session = freshMember("saver");
        Long userId = userIdOf(session);
        Persona persona = createPersona("문학");
        Post first = createPost(persona, "저장 첫째", "b", Instant.now().minusSeconds(10), "문학");
        Post second = createPost(persona, "저장 둘째", "b", Instant.now(), "문학");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(put("/api/v1/users/me/saved-posts/{id}", first.getId()).with(withSession(session)))
                    .andExpect(status().isNoContent());
        }
        Thread.sleep(5);
        mockMvc.perform(put("/api/v1/users/me/saved-posts/{id}", second.getId()).with(withSession(session)))
                .andExpect(status().isNoContent());
        assertThat(savedPostRepository.existsById(new SavedPostId(userId, first.getId()))).isTrue();

        JsonNode page1 = readJson(mockMvc.perform(get("/api/v1/users/me/saved-posts").param("limit", "1").session(session))
                .andExpect(status().isOk()).andReturn()).get("data");
        assertThat(page1.get("items")).hasSize(1);
        JsonNode item = page1.get("items").get(0);
        assertThat(item.get("post").get("id").asText()).isEqualTo(second.getId().toString());
        assertThat(item.get("savedAt").isTextual()).isTrue();
        assertThat(item.get("post").get("userState").get("saved").asBoolean()).isTrue();
        assertThat(item.get("post").get("sections")).hasSize(2);

        JsonNode page2 = readJson(mockMvc.perform(get("/api/v1/users/me/saved-posts").param("limit", "1").session(session)
                        .param("cursor", page1.get("nextCursor").asText()))
                .andExpect(status().isOk()).andReturn()).get("data");
        assertThat(page2.get("items").get(0).get("post").get("id").asText()).isEqualTo(first.getId().toString());
        assertThat(page2.get("nextCursor").isNull()).isTrue();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(delete("/api/v1/users/me/saved-posts/{id}", second.getId()).with(withSession(session)))
                    .andExpect(status().isNoContent());
        }
        JsonNode after = readJson(mockMvc.perform(get("/api/v1/users/me/saved-posts").session(session))
                .andExpect(status().isOk()).andReturn()).at("/data/items");
        assertThat(after).hasSize(1);
        assertThat(after.get(0).get("post").get("userState").get("saved").asBoolean()).isTrue();
    }

    @Test
    void hiddenPostDisappearsEverywhereButOwnRowsCanStillBeDeleted() throws Exception {
        MockHttpSession session = freshMember("hidden");
        Long userId = userIdOf(session);
        Persona persona = createPersona("심리");
        Post post = createPost(persona, "숨김예정 hid1", "b", Instant.now(), "심리");
        String postId = post.getId().toString();

        mockMvc.perform(put("/api/v1/users/me/posts/{id}/like", post.getId()).with(withSession(session)))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/users/me/saved-posts/{id}", post.getId()).with(withSession(session)))
                .andExpect(status().isNoContent());
        assertThat(ids(readJson(mockMvc.perform(get("/api/v1/feed").param("limit", "50")).andReturn()).at("/data/items")))
                .contains(postId);

        transactionTemplate.executeWithoutResult(status -> {
            Post managed = postRepository.findById(post.getId()).orElseThrow();
            managed.setStatus(PostStatus.HIDDEN);
        });

        mockMvc.perform(get("/api/v1/posts/{id}", post.getId()).session(session)).andExpect(status().isNotFound());
        assertThat(ids(readJson(mockMvc.perform(get("/api/v1/feed").param("limit", "50")).andReturn()).at("/data/items")))
                .doesNotContain(postId);
        assertThat(ids(readJson(mockMvc.perform(get("/api/v1/search").param("q", "hid1")).andReturn()).at("/data/items")))
                .isEmpty();
        assertThat(ids(readJson(mockMvc.perform(get("/api/v1/personas/{id}/posts", persona.getId())).andReturn())
                .at("/data/items"))).doesNotContain(postId);
        JsonNode saved = readJson(mockMvc.perform(get("/api/v1/users/me/saved-posts").session(session)).andReturn())
                .at("/data/items");
        List<String> savedIds = new ArrayList<>();
        saved.forEach(item -> savedIds.add(item.get("post").get("id").asText()));
        assertThat(savedIds).doesNotContain(postId);

        mockMvc.perform(put("/api/v1/users/me/posts/{id}/like", post.getId()).with(withSession(session)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/users/me/saved-posts/{id}", post.getId()).with(withSession(session)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/users/me/posts/{id}/like", post.getId()).with(withSession(session)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/users/me/saved-posts/{id}", post.getId()).with(withSession(session)))
                .andExpect(status().isNoContent());
        assertThat(likeRepository.existsById(new PostLikeId(userId, post.getId()))).isFalse();
        assertThat(savedPostRepository.existsById(new SavedPostId(userId, post.getId()))).isFalse();
    }

    @Test
    void deactivatedPersonaHidesProfilePostsAndFollows() throws Exception {
        MockHttpSession session = freshMember("deact");
        Persona persona = createPersona("역사·사회");
        Post post = createPost(persona, "비활성예정 dea1", "b", Instant.now(), "역사·사회");
        String personaId = persona.getId().toString();

        mockMvc.perform(put("/api/v1/users/me/follows/{id}", persona.getId()).with(withSession(session)))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/users/me/saved-posts/{id}", post.getId()).with(withSession(session)))
                .andExpect(status().isNoContent());

        transactionTemplate.executeWithoutResult(status -> {
            Persona managed = personaRepository.findById(persona.getId()).orElseThrow();
            managed.setActive(false);
        });

        mockMvc.perform(get("/api/v1/personas/{id}", persona.getId())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/personas/{id}/posts", persona.getId())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/posts/{id}", post.getId())).andExpect(status().isNotFound());
        assertThat(ids(readJson(mockMvc.perform(get("/api/v1/personas").param("limit", "50")).andReturn()).at("/data/items")))
                .doesNotContain(personaId);
        assertThat(ids(readJson(mockMvc.perform(get("/api/v1/personas/recommendations")).andReturn()).at("/data/items")))
                .doesNotContain(personaId);
        assertThat(ids(readJson(mockMvc.perform(get("/api/v1/feed").param("limit", "50")).andReturn()).at("/data/items")))
                .doesNotContain(post.getId().toString());
        assertThat(ids(readJson(mockMvc.perform(get("/api/v1/feed").param("following", "true").session(session))
                .andReturn()).at("/data/items")))
                .doesNotContain(post.getId().toString());
        assertThat(ids(readJson(mockMvc.perform(get("/api/v1/users/me/follows").session(session)).andReturn())
                .at("/data/items"))).doesNotContain(personaId);
        assertThat(readJson(mockMvc.perform(get("/api/v1/users/me/saved-posts").session(session)).andReturn())
                .at("/data/items")).isEmpty();

        mockMvc.perform(put("/api/v1/users/me/follows/{id}", persona.getId()).with(withSession(session)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/users/me/follows/{id}", persona.getId()).with(withSession(session)))
                .andExpect(status().isNoContent());
    }
}
