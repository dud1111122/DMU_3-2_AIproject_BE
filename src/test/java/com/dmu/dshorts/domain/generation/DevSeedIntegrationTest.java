package com.dmu.dshorts.domain.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.dmu.dshorts.domain.generation.seed.SeedData.Catalog;
import com.dmu.dshorts.domain.generation.seed.SeedData.PersonaSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.PostSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.Posts;
import com.dmu.dshorts.domain.generation.service.PostDraftValidator;
import com.dmu.dshorts.domain.learning.repository.AnswerOptionRepository;
import com.dmu.dshorts.domain.learning.repository.QuestionRepository;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.repository.KeyPointRepository;
import com.dmu.dshorts.domain.post.repository.PostRepository;
import com.dmu.dshorts.domain.post.repository.PostSectionRepository;
import com.dmu.dshorts.domain.post.repository.PostSourceRepository;
import com.dmu.dshorts.support.IntegrationTestSupport;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

/** The fallback seed: 30 posts (3 per persona), every one valid, all loaded by DevSeedLoader at startup. */
class DevSeedIntegrationTest extends IntegrationTestSupport {

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

    private <T> T read(String path, Class<T> type) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(in, type);
        }
    }

    @Test
    void seedFileHasThirtyValidPostsThreePerPersona() throws Exception {
        Catalog catalog = read("seed/catalog.json", Catalog.class);
        Posts posts = read("seed/posts.json", Posts.class);
        Map<String, Set<String>> personaTopics = catalog.personas().stream()
                .collect(Collectors.toMap(PersonaSeed::username, p -> Set.copyOf(p.topics())));

        assertThat(posts.posts()).hasSize(30);
        assertThat(catalog.personas()).hasSize(10);
        Map<String, Long> perPersona = posts.posts().stream()
                .collect(Collectors.groupingBy(PostSeed::personaUsername, Collectors.counting()));
        assertThat(perPersona).hasSize(10).allSatisfy((username, count) -> assertThat(count).isEqualTo(3L));
        assertThat(posts.posts().stream().map(PostSeed::title).distinct()).hasSize(30);

        for (PostSeed post : posts.posts()) {
            assertThatCode(() -> PostDraftValidator.validate(post, personaTopics.get(post.personaUsername())))
                    .as("seed post '%s'", post.title())
                    .doesNotThrowAnyException();
            post.sources().forEach(source -> assertThat(source.url()).as("urls stay null unless certain").isNull());
            post.sections().forEach(section -> assertThat(section.imagePath()).isNull());
        }
    }

    @Test
    @Transactional
    void seedPostsAreLoadedWithAllChildren() {
        List<Persona> personas = personaRepository.findAll();
        assertThat(personas).hasSizeGreaterThanOrEqualTo(10);
        for (String username : List.of("socrates", "nietzsche", "kant", "einstein", "marie.curie", "darwin",
                "leonardo.davinci", "sejong", "adam.smith", "yun.dongju")) {
            Persona persona = personaRepository.findByUsername(username).orElseThrow();
            List<Post> posts = postRepository.findAll().stream()
                    .filter(p -> p.getPersona().getId().equals(persona.getId()) && p.getAiModel() == null)
                    .toList();
            assertThat(posts).as("seed posts of %s", username).hasSize(3);
            for (Post post : posts) {
                assertThat(post.getTopics()).isNotEmpty();
                assertThat(sectionRepository.findAll().stream().filter(s -> s.getPostId().equals(post.getId())))
                        .hasSizeBetween(1, 10);
                long points = keyPointRepository.findAll().stream()
                        .filter(k -> k.getPostId().equals(post.getId())).count();
                assertThat(points).isBetween(2L, 4L);
                long questions = questionRepository.findAll().stream()
                        .filter(q -> q.getPostId().equals(post.getId())).count();
                assertThat(questions).isEqualTo(points);
                assertThat(sourceRepository.findAll().stream()
                        .filter(s -> s.getPostId().equals(post.getId()))).isNotEmpty()
                        .allSatisfy(s -> assertThat(s.isVerified()).isFalse());
            }
        }
        questionRepository.findAll().forEach(q -> assertThat(answerOptionRepository.findAll().stream()
                .filter(o -> o.getQuestionId().equals(q.getId())).count()).isEqualTo(4L));
    }
}
