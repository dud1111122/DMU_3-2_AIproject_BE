package com.dmu.dshorts.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dmu.dshorts.domain.learning.entity.AnswerOption;
import com.dmu.dshorts.domain.learning.entity.Question;
import com.dmu.dshorts.domain.learning.repository.AnswerOptionRepository;
import com.dmu.dshorts.domain.learning.repository.QuestionRepository;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.post.entity.KeyPoint;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.entity.PostSection;
import com.dmu.dshorts.domain.post.entity.PostStatus;
import com.dmu.dshorts.domain.post.repository.KeyPointRepository;
import com.dmu.dshorts.domain.post.repository.PostRepository;
import com.dmu.dshorts.domain.post.repository.PostSectionRepository;
import com.dmu.dshorts.domain.post.repository.PostSourceRepository;
import com.dmu.dshorts.domain.topic.repository.TopicRepository;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.visibility.VisibilityService;
import com.dmu.dshorts.support.IntegrationTestSupport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Boot + schema validation, health, dev seed content and visibility rules. */
class FoundationIntegrationTest extends IntegrationTestSupport {

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
    PostSourceRepository sourceRepository;
    @Autowired
    QuestionRepository questionRepository;
    @Autowired
    AnswerOptionRepository answerOptionRepository;
    @Autowired
    VisibilityService visibilityService;

    @Test
    void healthReportsAiNotConfigured() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.data.ai").value("not-configured"));
    }

    @Test
    void devSeedLoadsCatalogAndValidFixturePosts() {
        assertThat(topicRepository.findByDeletedAtIsNullOrderBySortOrderAscIdAsc()).hasSizeGreaterThanOrEqualTo(8);
        assertThat(personaRepository.findByUsername("socrates")).isPresent();
        assertThat(visibilityService.publicTopicsOfPersona(
                personaRepository.findByUsername("yun.dongju").orElseThrow().getId())).hasSize(4);

        List<Post> posts = postRepository.findAll();
        assertThat(posts).hasSizeGreaterThanOrEqualTo(3);
        for (Post post : posts) {
            List<PostSection> sections = sectionRepository.findByPostIdOrderBySectionOrderAsc(post.getId());
            assertThat(sections).hasSizeBetween(2, 10);
            for (int i = 0; i < sections.size(); i++) {
                assertThat(sections.get(i).getSectionOrder()).isEqualTo((short) (i + 1));
            }
            List<KeyPoint> points = keyPointRepository.findByPostIdOrderByPointOrderAsc(post.getId());
            assertThat(points).hasSizeBetween(2, 4);
            List<Question> questions = questionRepository.findByPostId(post.getId());
            assertThat(questions).hasSameSizeAs(points);
            List<AnswerOption> options = answerOptionRepository.findByQuestionIdInOrderByQuestionIdAscOptionOrderAsc(
                    questions.stream().map(Question::getId).toList());
            assertThat(options).hasSize(questions.size() * 4);
            assertThat(sourceRepository.findByPostIdOrderBySourceOrderAsc(post.getId()))
                    .isNotEmpty()
                    .allSatisfy(source -> assertThat(source.isVerified()).isFalse());
            assertThat(visibilityService.publicTopicsOfPost(post.getId())).isNotEmpty();
        }
    }

    @Test
    @Transactional
    void hiddenPostAndInactivePersonaAreNotVisible() {
        Post post = postRepository.findAll().get(0);
        Long postId = post.getId();
        assertThat(visibilityService.getVisiblePost(postId).getPersona().getDisplayName()).isNotBlank();

        post.setStatus(PostStatus.HIDDEN);
        postRepository.flush();
        assertThatThrownBy(() -> visibilityService.getVisiblePost(postId)).isInstanceOf(ApiException.class);

        post.setStatus(PostStatus.PUBLISHED);
        Persona persona = post.getPersona();
        persona.setActive(false);
        postRepository.flush();
        assertThat(visibilityService.isPostVisible(postId)).isFalse();
        assertThat(visibilityService.isPersonaVisible(persona.getId())).isFalse();

        persona.setActive(true);
        post.setDeletedAt(Instant.now());
        postRepository.flush();
        assertThat(visibilityService.visiblePostIds(List.of(postId))).isEmpty();
    }
}
