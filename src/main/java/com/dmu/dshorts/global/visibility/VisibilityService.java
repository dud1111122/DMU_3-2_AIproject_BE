package com.dmu.dshorts.global.visibility;

import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.post.entity.Post;
import com.dmu.dshorts.domain.post.repository.PostRepository;
import com.dmu.dshorts.domain.topic.entity.Topic;
import com.dmu.dshorts.domain.topic.repository.TopicRepository;
import com.dmu.dshorts.global.error.ApiException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place that decides what non-admin users may see. Every public or member feature
 * (feed, detail, quiz, chat, review, saved posts, debates, guest AI) must load posts/personas through here
 * or through the JPQL constants {@link PostRepository#VISIBLE_POST} / {@link PersonaRepository#VISIBLE_PERSONA}.
 *
 * <ul>
 *   <li>Post visible = status PUBLISHED, {@code deletedAt} null, persona active and not deleted.</li>
 *   <li>Persona visible = active and not deleted.</li>
 *   <li>Topic with {@code deletedAt} is dropped from public DTOs (links stay in the DB).</li>
 *   <li>Debate visible = both personas visible.</li>
 * </ul>
 * Invisible and non-existent look the same to clients: 404 NOT_FOUND.
 */
@Service
@Transactional(readOnly = true)
public class VisibilityService {

    private static final Comparator<Topic> TOPIC_ORDER =
            Comparator.comparingInt(Topic::getSortOrder).thenComparing(Topic::getId);

    private final PostRepository postRepository;
    private final PersonaRepository personaRepository;
    private final TopicRepository topicRepository;

    public VisibilityService(PostRepository postRepository, PersonaRepository personaRepository,
                             TopicRepository topicRepository) {
        this.postRepository = postRepository;
        this.personaRepository = personaRepository;
        this.topicRepository = topicRepository;
    }

    /** Visible post with {@code getPersona()} already loaded, or 404 NOT_FOUND. */
    public Post getVisiblePost(Long postId) {
        if (postId == null) {
            throw ApiException.notFound();
        }
        return postRepository.findVisibleById(postId).orElseThrow(ApiException::notFound);
    }

    /** Throws 404 NOT_FOUND unless the post is visible. */
    public void requireVisiblePost(Long postId) {
        if (!isPostVisible(postId)) {
            throw ApiException.notFound();
        }
    }

    public boolean isPostVisible(Long postId) {
        return postId != null && postRepository.findVisibleById(postId).isPresent();
    }

    /** The subset of {@code postIds} that is currently visible (useful for filtering stored records). */
    public Set<Long> visiblePostIds(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(postRepository.findVisibleIds(postIds));
    }

    /** Visible persona or 404 NOT_FOUND. */
    public Persona getVisiblePersona(Long personaId) {
        if (personaId == null) {
            throw ApiException.notFound();
        }
        return personaRepository.findVisibleById(personaId).orElseThrow(ApiException::notFound);
    }

    public boolean isPersonaVisible(Long personaId) {
        return personaId != null && personaRepository.findVisibleById(personaId).isPresent();
    }

    public Set<Long> visiblePersonaIds(Collection<Long> personaIds) {
        if (personaIds == null || personaIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(personaRepository.findVisibleIds(personaIds));
    }

    /** Debates are visible only while both participants are visible. */
    public boolean isDebateVisible(Long personaLowId, Long personaHighId) {
        return visiblePersonaIds(List.of(personaLowId, personaHighId)).size() == 2;
    }

    /** Non-deleted topics of a post for public DTOs, ordered by sortOrder, id. */
    public List<Topic> publicTopicsOfPost(Long postId) {
        return topicRepository.findPublicTopicsOfPost(postId);
    }

    /** Non-deleted topics of a persona for public DTOs, ordered by sortOrder, id. */
    public List<Topic> publicTopicsOfPersona(Long personaId) {
        return topicRepository.findPublicTopicsOfPersona(personaId);
    }

    /** Filters already loaded topics (e.g. {@code post.getTopics()} inside a transaction). */
    public static List<Topic> publicTopics(Collection<Topic> topics) {
        return topics.stream()
                .filter(topic -> !topic.isDeleted())
                .sorted(TOPIC_ORDER)
                .toList();
    }
}
