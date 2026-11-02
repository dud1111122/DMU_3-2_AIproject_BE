package com.dmu.dshorts.domain.persona.service;

import com.dmu.dshorts.domain.activity.repository.FollowRepository;
import com.dmu.dshorts.domain.activity.repository.UserInterestRepository;
import com.dmu.dshorts.domain.persona.dto.PersonaDetail;
import com.dmu.dshorts.domain.persona.dto.PersonaListItem;
import com.dmu.dshorts.domain.persona.dto.RecommendedPersona;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaQueryRepository;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.post.repository.LikePatterns;
import com.dmu.dshorts.domain.post.repository.PostRepository;
import com.dmu.dshorts.domain.topic.dto.TopicSummary;
import com.dmu.dshorts.domain.topic.entity.Topic;
import com.dmu.dshorts.domain.topic.repository.TopicRepository;
import com.dmu.dshorts.domain.topic.service.TopicService;
import com.dmu.dshorts.global.common.Cursors;
import com.dmu.dshorts.global.common.Ids;
import com.dmu.dshorts.global.common.PageResponse;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.security.AuthUser;
import com.dmu.dshorts.global.visibility.VisibilityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Public persona reads: list, recommendations, profile, persona search; also persona list items for follows. */
@Service
@Transactional(readOnly = true)
public class PersonaService {

    public static final int MAX_RECOMMENDATIONS = 10;
    public static final int MAX_RECOMMENDATION_TOPICS = 10;

    private final PersonaRepository personaRepository;
    private final PersonaQueryRepository personaQueryRepository;
    private final TopicRepository topicRepository;
    private final TopicService topicService;
    private final UserInterestRepository userInterestRepository;
    private final FollowRepository followRepository;
    private final PostRepository postRepository;
    private final VisibilityService visibilityService;

    public PersonaService(PersonaRepository personaRepository, PersonaQueryRepository personaQueryRepository,
                          TopicRepository topicRepository, TopicService topicService,
                          UserInterestRepository userInterestRepository, FollowRepository followRepository,
                          PostRepository postRepository, VisibilityService visibilityService) {
        this.personaRepository = personaRepository;
        this.personaQueryRepository = personaQueryRepository;
        this.topicRepository = topicRepository;
        this.topicService = topicService;
        this.userInterestRepository = userInterestRepository;
        this.followRepository = followRepository;
        this.postRepository = postRepository;
        this.visibilityService = visibilityService;
    }

    /** Visible personas by id asc; optional partial match on name/username/bio and topic filter. */
    public PageResponse<PersonaListItem> list(String q, Long topicId, String cursor, Integer limitParam) {
        int limit = Cursors.limit(limitParam);
        Long afterId = Cursors.decodeId(cursor);
        String pattern = q == null ? null : LikePatterns.contains(q);
        List<Persona> rows = personaQueryRepository.findVisible(pattern, topicId, null, afterId, limit + 1);
        return idPage(rows, limit);
    }

    /** Visible personas followed by the user, id asc. */
    public PageResponse<PersonaListItem> followedBy(Long userId, String cursor, Integer limitParam) {
        int limit = Cursors.limit(limitParam);
        Long afterId = Cursors.decodeId(cursor);
        List<Persona> rows = personaQueryRepository.findVisible(null, null, userId, afterId, limit + 1);
        return idPage(rows, limit);
    }

    /** Persona search on display name / username, id desc. */
    public PageResponse<PersonaListItem> search(String q, String cursor, Integer limitParam) {
        int limit = Cursors.limit(limitParam);
        Long beforeId = Cursors.decodeId(cursor);
        List<Persona> rows = personaQueryRepository.searchByName(LikePatterns.contains(q), beforeId, limit + 1);
        return idPage(rows, limit);
    }

    /**
     * Up to 10 visible personas ranked by the number of their (non-deleted) topics among the interest topics, then id.
     * Interests: {@code topicIdsCsv} when given (max 10 IDs); otherwise the member's stored interests; guests without
     * the parameter have none. No interests → visible personas by id.
     */
    public List<RecommendedPersona> recommendations(String topicIdsCsv, AuthUser viewer) {
        List<Long> requested;
        if (topicIdsCsv != null) {
            requested = List.copyOf(new LinkedHashSet<>(Ids.parseCsvIds(topicIdsCsv, "topicIds")));
            if (requested.size() > MAX_RECOMMENDATION_TOPICS) {
                throw ApiException.validation("topicIds", "주제는 최대 " + MAX_RECOMMENDATION_TOPICS + "개까지 선택할 수 있습니다.");
            }
        } else if (viewer != null) {
            requested = userInterestRepository.findActiveTopicIds(viewer.id());
        } else {
            requested = List.of();
        }
        List<Long> activeTopicIds = requested.isEmpty()
                ? List.of()
                : topicRepository.findByIdInAndDeletedAtIsNull(requested).stream().map(Topic::getId).toList();

        // -1 never matches: keeps the native IN list non-empty when there are no interests (all counts 0 → id order).
        List<Object[]> ranking = personaRepository.rankByTopicOverlap(
                activeTopicIds.isEmpty() ? List.of(-1L) : activeTopicIds, MAX_RECOMMENDATIONS);
        List<Long> ids = new ArrayList<>(ranking.size());
        for (Object[] row : ranking) {
            ids.add(((Number) row[0]).longValue());
        }
        Map<Long, Persona> byId = personaRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Persona::getId, Function.identity()));
        Map<Long, List<TopicSummary>> topics = topicService.publicTopicsOfPersonas(ids);

        List<RecommendedPersona> result = new ArrayList<>(ranking.size());
        for (Object[] row : ranking) {
            Long id = ((Number) row[0]).longValue();
            int overlap = ((Number) row[1]).intValue();
            result.add(RecommendedPersona.from(byId.get(id), topics.getOrDefault(id, List.of()), overlap));
        }
        return result;
    }

    /** Public profile or 404 when inactive/deleted/missing. */
    public PersonaDetail detail(Long personaId, AuthUser viewer) {
        Persona persona = visibilityService.getVisiblePersona(personaId);
        List<TopicSummary> topics = visibilityService.publicTopicsOfPersona(personaId).stream()
                .map(TopicSummary::from)
                .toList();
        boolean followed = viewer != null && !followRepository.findFollowedPersonaIds(viewer.id(), List.of(personaId))
                .isEmpty();
        return PersonaDetail.from(persona, topics, postRepository.countVisibleByPersonaId(personaId),
                followRepository.countByPersonaId(personaId), viewer != null, followed);
    }

    /** List items with topics batch-loaded, in the given order. */
    public List<PersonaListItem> toListItems(List<Persona> personas) {
        if (personas.isEmpty()) {
            return List.of();
        }
        Map<Long, List<TopicSummary>> topics =
                topicService.publicTopicsOfPersonas(personas.stream().map(Persona::getId).toList());
        return personas.stream()
                .map(persona -> PersonaListItem.from(persona, topics.getOrDefault(persona.getId(), List.of())))
                .toList();
    }

    private PageResponse<PersonaListItem> idPage(List<Persona> rows, int limit) {
        boolean hasMore = rows.size() > limit;
        List<Persona> kept = hasMore ? rows.subList(0, limit) : rows;
        String next = hasMore ? Cursors.encodeId(kept.get(kept.size() - 1).getId()) : null;
        return PageResponse.of(toListItems(kept), next);
    }
}
