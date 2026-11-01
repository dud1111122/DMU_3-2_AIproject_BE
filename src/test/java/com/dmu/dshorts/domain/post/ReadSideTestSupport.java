package com.dmu.dshorts.domain.post;

import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.entity.PostSection;
import com.dmu.dshorts.domain.post.repository.PostRepository;
import com.dmu.dshorts.domain.post.repository.PostSectionRepository;
import com.dmu.dshorts.domain.topic.entity.Topic;
import com.dmu.dshorts.domain.topic.repository.TopicRepository;
import com.dmu.dshorts.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Fixture helpers for read-side and activity tests: seed lookups and committed throw-away personas/posts. */
public abstract class ReadSideTestSupport extends IntegrationTestSupport {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    protected TopicRepository topicRepository;
    @Autowired
    protected PersonaRepository personaRepository;
    @Autowired
    protected PostRepository postRepository;
    @Autowired
    protected PostSectionRepository sectionRepository;
    @Autowired
    protected TransactionTemplate transactionTemplate;

    protected Persona persona(String username) {
        return personaRepository.findByUsername(username).orElseThrow();
    }

    protected Topic topic(String name) {
        return topicRepository.findByName(name).orElseThrow();
    }

    /** Seed post of a persona (the fixture has exactly one post per persona). */
    protected Post seedPost(String personaUsername) {
        Long personaId = persona(personaUsername).getId();
        return postRepository.findAll().stream()
                .filter(post -> post.getPersona().getId().equals(personaId))
                .findFirst()
                .orElseThrow();
    }

    /** Creates and commits a fresh visible persona with the given topics. */
    protected Persona createPersona(String... topicNames) {
        String username = "t." + (System.nanoTime() % 1_000_000) + SEQUENCE.incrementAndGet();
        return transactionTemplate.execute(status -> {
            Persona persona = new Persona(username, "테스트 인물 " + username);
            for (String name : topicNames) {
                persona.getTopics().add(topic(name));
            }
            return personaRepository.save(persona);
        });
    }

    /** Creates and commits a visible post (2 cards, 2nd with an image) for the persona, published at {@code at}. */
    protected Post createPost(Persona persona, String title, String body, Instant at, String... topicNames) {
        return transactionTemplate.execute(status -> {
            Post post = new Post(persona, title);
            post.setSummary("요약 " + title);
            post.setCaption("캡션");
            post.setPublishedAt(at);
            for (String name : topicNames) {
                post.getTopics().add(topic(name));
            }
            postRepository.save(post);
            sectionRepository.save(new PostSection(post.getId(), (short) 1, body));
            PostSection second = new PostSection(post.getId(), (short) 2, "둘째 카드 " + body);
            second.setImagePath("cards/" + post.getId() + "/2.webp");
            second.setImageAlt("대체 텍스트");
            second.setImageAiGenerated(true);
            second.setImageCredit("AI 생성");
            sectionRepository.save(second);
            return post;
        });
    }

    protected List<String> ids(JsonNode items) {
        List<String> ids = new ArrayList<>();
        items.forEach(item -> ids.add(item.get("id").asText()));
        return ids;
    }
}
