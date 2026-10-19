package com.dmu.dshorts.domain.generation.seed;

import com.dmu.dshorts.domain.generation.seed.SeedData.Catalog;
import com.dmu.dshorts.domain.generation.seed.SeedData.KeyPointSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.PersonaSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.PostSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.Posts;
import com.dmu.dshorts.domain.generation.seed.SeedData.QuestionSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.SectionSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.SourceSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.TopicSeed;
import com.dmu.dshorts.domain.learning.entity.AnswerOption;
import com.dmu.dshorts.domain.learning.entity.Question;
import com.dmu.dshorts.domain.learning.repository.AnswerOptionRepository;
import com.dmu.dshorts.domain.learning.repository.QuestionRepository;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.post.entity.CitationType;
import com.dmu.dshorts.domain.post.entity.KeyPoint;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.entity.PostSection;
import com.dmu.dshorts.domain.post.entity.PostSource;
import com.dmu.dshorts.domain.post.repository.KeyPointRepository;
import com.dmu.dshorts.domain.post.repository.PostRepository;
import com.dmu.dshorts.domain.post.repository.PostSectionRepository;
import com.dmu.dshorts.domain.post.repository.PostSourceRepository;
import com.dmu.dshorts.domain.topic.entity.Topic;
import com.dmu.dshorts.domain.topic.repository.TopicRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Development seed. On startup, when {@code app.seed.dev-data=true} (env DEV_SEED, default true) and the
 * {@code topics} table is empty, loads in ONE transaction:
 * <ol>
 *   <li>{@code seed/catalog.json}: 8 topics, 10 personas with their AI settings and persona_topics links
 *       (same content as 기획/seed_catalog.sql);</li>
 *   <li>{@code seed/posts.json}: fixture posts with cards, key points, one 4-option question per key point
 *       and AI-suggested (verified=false) sources.</li>
 * </ol>
 * Every post is validated against the content rules first ({@link #validate(PostSeed, Map)}); an invalid
 * seed file fails startup. To add posts, append to posts.json — no code change needed.
 */
@Component
@Order(1)
public class DevSeedLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeedLoader.class);

    static final String CATALOG_PATH = "seed/catalog.json";
    static final String POSTS_PATH = "seed/posts.json";

    private final boolean enabled;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final TopicRepository topicRepository;
    private final PersonaRepository personaRepository;
    private final PostRepository postRepository;
    private final PostSectionRepository sectionRepository;
    private final KeyPointRepository keyPointRepository;
    private final PostSourceRepository sourceRepository;
    private final QuestionRepository questionRepository;
    private final AnswerOptionRepository answerOptionRepository;

    public DevSeedLoader(@Value("${app.seed.dev-data:true}") boolean enabled,
                         ObjectMapper objectMapper,
                         TransactionTemplate transactionTemplate,
                         TopicRepository topicRepository,
                         PersonaRepository personaRepository,
                         PostRepository postRepository,
                         PostSectionRepository sectionRepository,
                         KeyPointRepository keyPointRepository,
                         PostSourceRepository sourceRepository,
                         QuestionRepository questionRepository,
                         AnswerOptionRepository answerOptionRepository) {
        this.enabled = enabled;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.topicRepository = topicRepository;
        this.personaRepository = personaRepository;
        this.postRepository = postRepository;
        this.sectionRepository = sectionRepository;
        this.keyPointRepository = keyPointRepository;
        this.sourceRepository = sourceRepository;
        this.questionRepository = questionRepository;
        this.answerOptionRepository = answerOptionRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (topicRepository.count() > 0) {
            log.info("Dev seed skipped: topics already exist");
            return;
        }
        Catalog catalog = read(CATALOG_PATH, Catalog.class);
        Posts posts = read(POSTS_PATH, Posts.class);
        Map<String, Set<String>> personaTopics = catalog.personas().stream()
                .collect(Collectors.toMap(PersonaSeed::username, persona -> Set.copyOf(persona.topics())));
        posts.posts().forEach(post -> validate(post, personaTopics));

        transactionTemplate.executeWithoutResult(status -> {
            Map<String, Topic> topicsByName = saveTopics(catalog.topics());
            savePersonas(catalog.personas(), topicsByName);
            Instant now = Instant.now();
            for (PostSeed post : posts.posts()) {
                savePost(post, topicsByName, now);
            }
        });
        log.info("Dev seed loaded: {} topics, {} personas, {} posts",
                catalog.topics().size(), catalog.personas().size(), posts.posts().size());
    }

    private Map<String, Topic> saveTopics(List<TopicSeed> seeds) {
        Map<String, Topic> byName = new HashMap<>();
        for (TopicSeed seed : seeds) {
            Topic topic = new Topic(seed.name());
            topic.setDescription(seed.description());
            topic.setSortOrder(seed.sortOrder());
            byName.put(seed.name(), topicRepository.save(topic));
        }
        return byName;
    }

    private void savePersonas(List<PersonaSeed> seeds, Map<String, Topic> topicsByName) {
        for (PersonaSeed seed : seeds) {
            Persona persona = new Persona(seed.username(), seed.displayName());
            persona.setEra(seed.era());
            persona.setBio(seed.bio());
            persona.setPerspective(seed.perspective());
            persona.setVoice(seed.voice());
            persona.setKnowledgeScope(seed.knowledgeScope());
            persona.setProhibitions(seed.prohibitions());
            persona.setConversationRules(seed.conversationRules());
            for (String topicName : seed.topics()) {
                persona.getTopics().add(requireTopic(topicsByName, topicName));
            }
            personaRepository.save(persona);
        }
    }

    private void savePost(PostSeed seed, Map<String, Topic> topicsByName, Instant now) {
        Persona persona = personaRepository.findByUsername(seed.personaUsername())
                .orElseThrow(() -> new IllegalStateException("Unknown persona in seed: " + seed.personaUsername()));
        Post post = new Post(persona, seed.title());
        post.setSummary(nullToEmpty(seed.summary()));
        post.setCaption(nullToEmpty(seed.caption()));
        int hoursAgo = seed.publishedHoursAgo() == null ? 0 : seed.publishedHoursAgo();
        post.setPublishedAt(now.minus(Duration.ofHours(hoursAgo)));
        for (String topicName : seed.topics()) {
            post.getTopics().add(requireTopic(topicsByName, topicName));
        }
        postRepository.save(post);
        Long postId = post.getId();

        List<Long> sectionIds = new ArrayList<>();
        for (int i = 0; i < seed.sections().size(); i++) {
            SectionSeed sectionSeed = seed.sections().get(i);
            PostSection section = new PostSection(postId, (short) (i + 1), sectionSeed.body());
            section.setTitle(sectionSeed.title());
            section.setCitationType(sectionSeed.citationType() == null
                    ? CitationType.RECONSTRUCTION
                    : CitationType.valueOf(sectionSeed.citationType()));
            section.setQuoteAttribution(sectionSeed.quoteAttribution());
            section.setImagePath(sectionSeed.imagePath());
            section.setImageAlt(sectionSeed.imageAlt());
            section.setImageMime(sectionSeed.imageMime());
            section.setImageWidth(sectionSeed.imageWidth());
            section.setImageHeight(sectionSeed.imageHeight());
            section.setImageAiGenerated(Boolean.TRUE.equals(sectionSeed.imageAiGenerated()));
            section.setImageCredit(sectionSeed.imageCredit());
            sectionIds.add(sectionRepository.save(section).getId());
        }

        for (int i = 0; i < seed.keyPoints().size(); i++) {
            KeyPointSeed pointSeed = seed.keyPoints().get(i);
            KeyPoint keyPoint = new KeyPoint(postId, (short) (i + 1), pointSeed.text());
            if (pointSeed.sectionOrder() != null) {
                keyPoint.setSectionId(sectionIds.get(pointSeed.sectionOrder() - 1));
            }
            keyPointRepository.save(keyPoint);

            QuestionSeed questionSeed = pointSeed.question();
            // questions.correct_option_order → answer_options is DEFERRABLE: options are inserted in the same tx.
            Question question = questionRepository.save(new Question(postId, keyPoint.getId(),
                    questionSeed.prompt(), questionSeed.explanation(), (short) questionSeed.correctOptionOrder()));
            for (int j = 0; j < questionSeed.options().size(); j++) {
                answerOptionRepository.save(new AnswerOption(question.getId(), (short) (j + 1),
                        questionSeed.options().get(j)));
            }
        }

        for (int i = 0; i < seed.sources().size(); i++) {
            SourceSeed sourceSeed = seed.sources().get(i);
            PostSource source = new PostSource(postId, (short) (i + 1), sourceSeed.title());
            source.setAuthor(sourceSeed.author());
            source.setPublisher(sourceSeed.publisher());
            source.setUrl(sourceSeed.url());
            source.setPublicationLabel(sourceSeed.publicationLabel());
            source.setNote(sourceSeed.note());
            source.setVerified(false);
            sourceRepository.save(source);
        }
    }

    /** Content rules from 기획.md / DB_설계.md that the DB cannot check by itself. */
    static void validate(PostSeed post, Map<String, Set<String>> personaTopics) {
        String label = "seed post '" + post.title() + "'";
        require(post.title() != null && !post.title().isBlank() && post.title().length() <= 200, label + ": title");
        Set<String> allowedTopics = personaTopics.get(post.personaUsername());
        require(allowedTopics != null, label + ": unknown persona " + post.personaUsername());
        require(post.topics() != null && !post.topics().isEmpty()
                && allowedTopics.containsAll(post.topics()), label + ": topics must be the persona's topics");

        int sectionCount = post.sections() == null ? 0 : post.sections().size();
        require(sectionCount >= 2 && sectionCount <= 10, label + ": needs 2..10 sections");
        for (SectionSeed section : post.sections()) {
            require(section.body() != null && !section.body().isBlank(), label + ": section body required");
            require(!"DIRECT_QUOTE".equals(section.citationType()) || section.quoteAttribution() != null,
                    label + ": DIRECT_QUOTE needs quoteAttribution");
            require(section.imagePath() == null || (section.imageAlt() != null && !section.imageAlt().isBlank()),
                    label + ": image needs imageAlt");
        }

        int pointCount = post.keyPoints() == null ? 0 : post.keyPoints().size();
        require(pointCount >= 2 && pointCount <= 4, label + ": needs 2..4 key points");
        for (KeyPointSeed point : post.keyPoints()) {
            require(point.text() != null && !point.text().isBlank() && point.text().length() <= 500,
                    label + ": key point text");
            require(point.sectionOrder() == null
                    || (point.sectionOrder() >= 1 && point.sectionOrder() <= sectionCount),
                    label + ": key point sectionOrder out of range");
            QuestionSeed question = point.question();
            require(question != null, label + ": every key point needs one question");
            require(question.options() != null && question.options().size() == 4,
                    label + ": question needs exactly 4 options");
            require(question.correctOptionOrder() >= 1 && question.correctOptionOrder() <= 4,
                    label + ": correctOptionOrder must be 1..4");
            require(question.prompt() != null && question.explanation() != null, label + ": question text");
        }

        require(post.sources() != null && !post.sources().isEmpty(), label + ": needs at least one source");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Invalid dev seed: " + message);
        }
    }

    private static Topic requireTopic(Map<String, Topic> topicsByName, String name) {
        Topic topic = topicsByName.get(name);
        if (topic == null) {
            throw new IllegalStateException("Unknown topic in seed: " + name);
        }
        return topic;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private <T> T read(String path, Class<T> type) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(in, type);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read " + path, ex);
        }
    }
}
