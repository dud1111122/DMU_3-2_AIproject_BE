package com.dmu.dshorts.domain.generation.seed;

import com.dmu.dshorts.domain.generation.seed.SeedData.Catalog;
import com.dmu.dshorts.domain.generation.seed.SeedData.PersonaSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.PostSeed;
import com.dmu.dshorts.domain.generation.seed.SeedData.Posts;
import com.dmu.dshorts.domain.generation.seed.SeedData.TopicSeed;
import com.dmu.dshorts.domain.generation.service.InvalidPostDraftException;
import com.dmu.dshorts.domain.generation.service.PostDraftValidator;
import com.dmu.dshorts.domain.generation.service.PostWriter;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.topic.entity.Topic;
import com.dmu.dshorts.domain.topic.repository.TopicRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
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
 * Every post is validated against the content rules first ({@link PostDraftValidator}); an invalid
 * seed file fails startup. Posts are stored by {@link PostWriter}, the same code path as AI preparation.
 * To add posts, append to posts.json — no code change needed. When the app is started as the preparation command
 * ({@code --prepare-posts=<runKey>}) only the catalog is loaded, because the AI writes the posts.
 */
@Component
@Order(1)
public class DevSeedLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeedLoader.class);

    static final String CATALOG_PATH = "seed/catalog.json";
    static final String POSTS_PATH = "seed/posts.json";
    static final String PREPARE_OPTION = "prepare-posts";

    private final boolean enabled;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final TopicRepository topicRepository;
    private final PersonaRepository personaRepository;
    private final PostWriter postWriter;

    public DevSeedLoader(@Value("${app.seed.dev-data:true}") boolean enabled,
                         ObjectMapper objectMapper,
                         TransactionTemplate transactionTemplate,
                         TopicRepository topicRepository,
                         PersonaRepository personaRepository,
                         PostWriter postWriter) {
        this.enabled = enabled;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.topicRepository = topicRepository;
        this.personaRepository = personaRepository;
        this.postWriter = postWriter;
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
        // In preparation mode (--prepare-posts=<runKey>) the AI writes the posts: load only the catalog.
        boolean catalogOnly = args.containsOption(PREPARE_OPTION);
        Posts posts = catalogOnly ? new Posts(List.of()) : read(POSTS_PATH, Posts.class);
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
        List<Topic> topics = seed.topics().stream().map(name -> requireTopic(topicsByName, name)).toList();
        int hoursAgo = seed.publishedHoursAgo() == null ? 0 : seed.publishedHoursAgo();
        postWriter.save(seed, persona, topics, now.minus(Duration.ofHours(hoursAgo)), null);
    }

    /**
     * Content rules shared with AI preparation ({@link PostDraftValidator}) plus: the persona exists in the catalog.
     *
     * @throws IllegalStateException naming the post and the broken rule
     */
    static void validate(PostSeed post, Map<String, Set<String>> personaTopics) {
        String label = "seed post '" + (post == null ? null : post.title()) + "'";
        Set<String> allowedTopics = post == null ? null : personaTopics.get(post.personaUsername());
        if (allowedTopics == null) {
            throw new IllegalStateException("Invalid dev seed: " + label + ": unknown persona");
        }
        try {
            PostDraftValidator.validate(post, allowedTopics);
        } catch (InvalidPostDraftException ex) {
            throw new IllegalStateException("Invalid dev seed: " + label + ": " + ex.getMessage(), ex);
        }
    }

    private static Topic requireTopic(Map<String, Topic> topicsByName, String name) {
        Topic topic = topicsByName.get(name);
        if (topic == null) {
            throw new IllegalStateException("Unknown topic in seed: " + name);
        }
        return topic;
    }

    private <T> T read(String path, Class<T> type) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(in, type);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read " + path, ex);
        }
    }
}
