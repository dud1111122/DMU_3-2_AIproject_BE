package com.dmu.dshorts.domain.admin.service;

import com.dmu.dshorts.domain.admin.dto.AdminPersonaDtos.AdminPersonaDetail;
import com.dmu.dshorts.domain.admin.dto.AdminPersonaDtos.AdminPersonaSummary;
import com.dmu.dshorts.domain.admin.dto.AdminPersonaDtos.PersonaRequest;
import com.dmu.dshorts.domain.admin.media.MediaStorage;
import com.dmu.dshorts.domain.admin.repository.AdminPersonaQueries;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.persona.repository.PersonaRepository;
import com.dmu.dshorts.domain.topic.entity.Topic;
import com.dmu.dshorts.domain.topic.repository.TopicRepository;
import com.dmu.dshorts.global.common.Ids;
import com.dmu.dshorts.global.error.ApiException;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AdminPersonaService {

    /** Same as the DB CHECK on personas.username. */
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9._]{3,30}$");

    private final PersonaRepository personaRepository;
    private final AdminPersonaQueries personaQueries;
    private final TopicRepository topicRepository;
    private final MediaStorage mediaStorage;

    public AdminPersonaService(PersonaRepository personaRepository, AdminPersonaQueries personaQueries,
                               TopicRepository topicRepository, MediaStorage mediaStorage) {
        this.personaRepository = personaRepository;
        this.personaQueries = personaQueries;
        this.topicRepository = topicRepository;
        this.mediaStorage = mediaStorage;
    }

    /** Every persona (inactive and deleted included), id order. */
    @Transactional(readOnly = true)
    public List<AdminPersonaSummary> list() {
        return personaQueries.findAllWithTopics().stream().map(AdminPersonaSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public AdminPersonaDetail get(Long id) {
        return AdminPersonaDetail.from(load(id));
    }

    public AdminPersonaDetail create(PersonaRequest request) {
        String username = username(request.username());
        ensureUsernameFree(username, null);
        Persona persona = new Persona(username, AdminText.requireText(request.displayName(), "displayName", 50));
        persona.setEra(AdminText.optionalText(request.era(), "era", 100, null));
        persona.setBio(AdminText.optionalText(request.bio(), "bio", 1000, ""));
        if (request.avatarPath() != null && !request.avatarPath().isBlank()) {
            persona.setAvatarPath(avatar(request.avatarPath()));
        }
        if (request.aiDisclosure() != null) {
            persona.setAiDisclosure(AdminText.requireText(request.aiDisclosure(), "aiDisclosure", 0));
        }
        applySettings(persona, request);
        if (request.active() != null) {
            persona.setActive(request.active());
        }
        if (request.topicIds() != null) {
            replaceTopics(persona, request.topicIds());
        }
        return AdminPersonaDetail.from(personaRepository.saveAndFlush(persona));
    }

    public AdminPersonaDetail update(Long id, PersonaRequest request) {
        Persona persona = load(id);
        if (persona.getDeletedAt() != null) {
            throw ApiException.conflict("삭제된 페르소나는 수정할 수 없습니다.");
        }
        if (request.username() != null) {
            String username = username(request.username());
            ensureUsernameFree(username, persona.getId());
            persona.setUsername(username);
        }
        if (request.displayName() != null) {
            persona.setDisplayName(AdminText.requireText(request.displayName(), "displayName", 50));
        }
        if (request.era() != null) {
            persona.setEra(AdminText.optionalText(request.era(), "era", 100, null));
        }
        if (request.bio() != null) {
            persona.setBio(AdminText.optionalText(request.bio(), "bio", 1000, ""));
        }
        if (request.avatarPath() != null) {
            String path = request.avatarPath().trim();
            if (path.isEmpty()) {
                persona.setAvatarPath(null);
            } else if (!path.equals(persona.getAvatarPath())) {
                persona.setAvatarPath(avatar(path));
            }
        }
        if (request.aiDisclosure() != null) {
            persona.setAiDisclosure(AdminText.requireText(request.aiDisclosure(), "aiDisclosure", 0));
        }
        applySettings(persona, request);
        if (request.active() != null) {
            persona.setActive(request.active());
        }
        if (request.topicIds() != null) {
            replaceTopics(persona, request.topicIds());
        }
        persona.setUpdatedAt(Instant.now());
        return AdminPersonaDetail.from(personaRepository.saveAndFlush(persona));
    }

    /** Logical delete + inactive. Idempotent. All related content becomes invisible to users. */
    public void delete(Long id) {
        Persona persona = personaRepository.findById(id).orElseThrow(ApiException::notFound);
        if (persona.getDeletedAt() == null) {
            persona.setDeletedAt(Instant.now());
        }
        persona.setActive(false);
        persona.setUpdatedAt(Instant.now());
    }

    private Persona load(Long id) {
        return personaQueries.findWithTopicsById(id).orElseThrow(ApiException::notFound);
    }

    private void applySettings(Persona persona, PersonaRequest request) {
        if (request.perspective() != null) {
            persona.setPerspective(request.perspective().trim());
        }
        if (request.voice() != null) {
            persona.setVoice(request.voice().trim());
        }
        if (request.knowledgeScope() != null) {
            persona.setKnowledgeScope(request.knowledgeScope().trim());
        }
        if (request.prohibitions() != null) {
            persona.setProhibitions(request.prohibitions().trim());
        }
        if (request.conversationRules() != null) {
            persona.setConversationRules(request.conversationRules().trim());
        }
    }

    /**
     * Full replacement of persona_topics. Every ID must exist; newly added links must point at non-deleted topics
     * (an existing link to a since-deleted topic may be kept).
     */
    private void replaceTopics(Persona persona, List<String> rawIds) {
        Set<Long> ids = new LinkedHashSet<>(Ids.parseBodyIds(rawIds, "topicIds"));
        Map<Long, Topic> found = topicRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Topic::getId, Function.identity()));
        Set<Long> current = new HashSet<>();
        persona.getTopics().forEach(topic -> current.add(topic.getId()));
        for (Long topicId : ids) {
            Topic topic = found.get(topicId);
            if (topic == null || (topic.isDeleted() && !current.contains(topicId))) {
                throw ApiException.validation("topicIds", "존재하지 않거나 삭제된 주제입니다: " + topicId);
            }
        }
        persona.getTopics().clear();
        ids.forEach(topicId -> persona.getTopics().add(found.get(topicId)));
    }

    private String avatar(String path) {
        String trimmed = path.trim();
        mediaStorage.requireExistingImage(trimmed, "avatarPath");
        return trimmed;
    }

    private static String username(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!USERNAME.matcher(value).matches()) {
            throw ApiException.validation("username", "영문 소문자·숫자·마침표·밑줄 3~30자여야 합니다.");
        }
        return value;
    }

    private void ensureUsernameFree(String username, Long selfId) {
        personaRepository.findByUsername(username)
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw ApiException.conflict("이미 사용 중인 페르소나 아이디입니다.");
                });
    }
}
