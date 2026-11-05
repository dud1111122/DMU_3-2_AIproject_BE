package com.dmu.dshorts.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.post.entity.KeyPoint;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.entity.PostSection;
import com.dmu.dshorts.domain.post.entity.PostStatus;
import com.dmu.dshorts.domain.post.repository.KeyPointRepository;
import com.dmu.dshorts.domain.post.repository.PostRepository;
import com.dmu.dshorts.domain.post.repository.PostSectionRepository;
import com.dmu.dshorts.domain.topic.entity.Topic;
import com.dmu.dshorts.domain.topic.repository.TopicRepository;
import com.dmu.dshorts.global.visibility.VisibilityService;
import com.dmu.dshorts.support.IntegrationTestSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** Admin topics, personas, posts, images. Each test rolls back; uploads go to a temp dir. */
@Transactional
class AdminManagementIntegrationTest extends IntegrationTestSupport {

    private static final Path MEDIA_DIR = createMediaDir();
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    TopicRepository topicRepository;
    @Autowired
    PersonaRepository personaRepository;
    @Autowired
    PostRepository postRepository;
    @Autowired
    PostSectionRepository sectionRepository;
    @Autowired
    KeyPointRepository keyPointRepository;
    @Autowired
    VisibilityService visibilityService;

    private static Path createMediaDir() {
        try {
            return Files.createTempDirectory("dshorts-admin-media-test");
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @DynamicPropertySource
    static void mediaDir(DynamicPropertyRegistry registry) {
        registry.add("app.media.dir", () -> MEDIA_DIR.toString());
    }

    @Test
    void topicCrudDuplicateNamesAndLogicalDelete() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        String name = "주제-" + unique();
        MvcResult created = mockMvc.perform(post("/api/v1/admin/topics").with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "description", "설명", "sortOrder", 99))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(name))
                .andExpect(jsonPath("$.data.deleted").value(false))
                .andReturn();
        String id = readJson(created).path("data").path("id").asText();

        mockMvc.perform(post("/api/v1/admin/topics").with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));

        mockMvc.perform(patch("/api/v1/admin/topics/{id}", id).with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sortOrder", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder").value(5));

        mockMvc.perform(delete("/api/v1/admin/topics/{id}", id).with(withSession(admin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/topics").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == '" + id + "')].deleted").value(org.hamcrest.Matchers.contains(true)));
        assertThat(topicRepository.findById(Long.valueOf(id)).orElseThrow().isDeleted()).isTrue();

        mockMvc.perform(post("/api/v1/admin/topics").with(withSession(loginAsDemo()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "권한없음-" + unique()))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/topics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void personaCreateUpdateAndDeleteHidesEverything() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        Topic topic = topicRepository.saveAndFlush(new Topic("topic-" + unique()));
        String username = "ps" + unique();

        MvcResult created = mockMvc.perform(post("/api/v1/admin/personas").with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "displayName", "테스트 인물",
                                "perspective", "내부 관점",
                                "voice", "내부 말투",
                                "topicIds", List.of(String.valueOf(topic.getId()))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.perspective").value("내부 관점"))
                .andExpect(jsonPath("$.data.topics[0].id").value(String.valueOf(topic.getId())))
                .andReturn();
        String id = readJson(created).path("data").path("id").asText();

        mockMvc.perform(post("/api/v1/admin/personas").with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "displayName", "중복"))))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/admin/personas").with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "AB", "displayName", "잘못된 아이디"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/admin/personas/{id}", id).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.voice").value("내부 말투"));

        mockMvc.perform(patch("/api/v1/admin/personas/{id}", id).with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("topicIds", List.of(), "displayName", "수정된 이름"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("수정된 이름"))
                .andExpect(jsonPath("$.data.topics").isEmpty());

        Persona persona = personaRepository.findById(Long.valueOf(id)).orElseThrow();
        Post post = newPost(persona, PostStatus.PUBLISHED);
        assertThat(visibilityService.isPostVisible(post.getId())).isTrue();

        mockMvc.perform(delete("/api/v1/admin/personas/{id}", id).with(withSession(admin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/personas").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == '" + id + "')].active").value(org.hamcrest.Matchers.contains(false)));
        mockMvc.perform(get("/api/v1/admin/personas/{id}", id).session(admin))
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.deleted").value(true));
        assertThat(visibilityService.isPersonaVisible(persona.getId())).isFalse();
        assertThat(visibilityService.isPostVisible(post.getId())).isFalse();
    }

    @Test
    void postEditsStructureRulesAndVisibility() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        Post post = newPost(newPersona(), PostStatus.PUBLISHED);
        PostSection section = sectionRepository.saveAndFlush(new PostSection(post.getId(), (short) 1, "원래 본문"));
        keyPointRepository.saveAndFlush(new KeyPoint(post.getId(), (short) 1, "핵심 1"));
        String id = String.valueOf(post.getId());

        mockMvc.perform(get("/api/v1/admin/posts/{id}", id).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visible").value(true))
                .andExpect(jsonPath("$.data.keyPoints[0].text").value("핵심 1"));

        mockMvc.perform(patch("/api/v1/admin/posts/{id}", id).with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"새 제목","summary":"새 요약","caption":"새 캡션",
                                 "sections":[{"id":"%s","body":"고친 본문","title":"카드 제목"}],
                                 "sources":[{"title":"참고 문헌","verified":true,"checkedAt":"2026-11-01"}]}
                                """.formatted(section.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("새 제목"))
                .andExpect(jsonPath("$.data.sections[0].body").value("고친 본문"))
                .andExpect(jsonPath("$.data.sources[0].verified").value(true))
                .andExpect(jsonPath("$.data.sources[0].checkedAt").value("2026-11-01"));

        // Learning structure and card structure are fixed after publishing.
        mockMvc.perform(patch("/api/v1/admin/posts/{id}", id).with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyPoints\":[{\"text\":\"바뀐 포인트\"}]}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/admin/posts/{id}", id).with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sections\":[{\"id\":\"%s\",\"order\":2}]}".formatted(section.getId())))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/v1/admin/posts/{id}", id).with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sections\":[{\"id\":\"%s\",\"delete\":true}]}".formatted(section.getId())))
                .andExpect(status().isConflict());

        mockMvc.perform(patch("/api/v1/admin/posts/{id}/visibility", id).with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "HIDDEN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));
        assertThat(visibilityService.isPostVisible(post.getId())).isFalse();
        mockMvc.perform(get("/api/v1/admin/posts/{id}", id).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HIDDEN"))
                .andExpect(jsonPath("$.data.visible").value(false));
        mockMvc.perform(patch("/api/v1/admin/posts/{id}/visibility", id).with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "NOPE"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/admin/posts/{id}/visibility", id).with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "PUBLISHED"))))
                .andExpect(status().isOk());
        assertThat(visibilityService.isPostVisible(post.getId())).isTrue();

        mockMvc.perform(delete("/api/v1/admin/posts/{id}", id).with(withSession(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/admin/posts/{id}", id).session(admin))
                .andExpect(jsonPath("$.data.deleted").value(true));
        mockMvc.perform(get("/api/v1/admin/posts").session(admin))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + id + "')]").isEmpty());
        mockMvc.perform(get("/api/v1/admin/posts").session(admin).param("includeDeleted", "true").param("limit", "50"))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + id + "')].deleted").value(org.hamcrest.Matchers.contains(true)));
        mockMvc.perform(patch("/api/v1/admin/posts/{id}/visibility", id).with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "HIDDEN"))))
                .andExpect(status().isConflict());
        assertThat(visibilityService.isPostVisible(post.getId())).isFalse();

        mockMvc.perform(get("/api/v1/admin/posts").session(loginAsDemo()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/posts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void imageUploadValidatesMimeServesFileAndFeedsCardReferences() throws Exception {
        MockHttpSession admin = loginAsAdmin();
        MvcResult up = mockMvc.perform(multipart("/api/v1/admin/images")
                        .file(new MockMultipartFile("file", "card.png", "image/png", png(3, 2)))
                        .with(withSession(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mime").value("image/png"))
                .andExpect(jsonPath("$.data.width").value(3))
                .andExpect(jsonPath("$.data.height").value(2))
                .andReturn();
        String path = readJson(up).path("data").path("path").asText();
        String url = readJson(up).path("data").path("url").asText();
        assertThat(path).startsWith("uploads/").endsWith(".png");
        assertThat(url).isEqualTo("/media/" + path);
        assertThat(Files.exists(MEDIA_DIR.resolve(path))).isTrue();

        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));

        mockMvc.perform(multipart("/api/v1/admin/images")
                        .file(new MockMultipartFile("file", "jpeg.jpg", "image/jpeg", jpeg(4, 4)))
                        .with(withSession(admin)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mime").value("image/jpeg"));

        mockMvc.perform(multipart("/api/v1/admin/images")
                        .file(new MockMultipartFile("file", "notes.png", "image/png", "not an image".getBytes()))
                        .with(withSession(admin)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mockMvc.perform(multipart("/api/v1/admin/images")
                        .file(new MockMultipartFile("file", "card.png", "text/plain", png(2, 2)))
                        .with(withSession(admin)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart("/api/v1/admin/images")
                        .file(new MockMultipartFile("file", "card.png", "image/png", png(2, 2)))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        Post post = newPost(newPersona(), PostStatus.PUBLISHED);
        PostSection section = sectionRepository.saveAndFlush(new PostSection(post.getId(), (short) 1, "본문"));
        String id = String.valueOf(post.getId());

        mockMvc.perform(patch("/api/v1/admin/posts/{id}", id).with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sectionImage(section, "uploads/missing.png", "그림")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/admin/posts/{id}", id).with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sectionImage(section, "../secret.png", "그림")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/admin/posts/{id}", id).with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sectionImage(section, path, "  ")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/admin/posts/{id}", id).with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sectionImage(section, path, "책과 노트")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections[0].image.url").value(url))
                .andExpect(jsonPath("$.data.sections[0].image.altText").value("책과 노트"))
                .andExpect(jsonPath("$.data.sections[0].image.width").value(3));

        mockMvc.perform(patch("/api/v1/admin/posts/{id}", id).with(withSession(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sections\":[{\"id\":\"%s\",\"image\":null}]}".formatted(section.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections[0].image").isEmpty());
    }

    private String sectionImage(PostSection section, String path, String altText) {
        return "{\"sections\":[{\"id\":\"%s\",\"image\":{\"path\":\"%s\",\"altText\":\"%s\"}}]}"
                .formatted(section.getId(), path, altText);
    }

    private Persona newPersona() {
        Persona persona = new Persona("ps" + unique(), "테스트 인물");
        persona.setActive(true);
        return personaRepository.saveAndFlush(persona);
    }

    private Post newPost(Persona persona, PostStatus status) {
        Post post = new Post(persona, "제목 " + unique());
        post.setSummary("요약");
        post.setCaption("캡션");
        post.setStatus(status);
        return postRepository.saveAndFlush(post);
    }

    private static String unique() {
        return System.nanoTime() % 1_000_000_000L + "" + SEQUENCE.incrementAndGet();
    }

    private static byte[] png(int width, int height) throws IOException {
        return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png");
    }

    private static byte[] jpeg(int width, int height) throws IOException {
        return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg");
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }
}
