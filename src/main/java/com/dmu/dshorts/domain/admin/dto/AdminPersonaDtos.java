package com.dmu.dshorts.domain.admin.dto;

import com.dmu.dshorts.domain.admin.dto.AdminTopicDtos.AdminTopicRef;
import com.dmu.dshorts.domain.admin.media.MediaStorage;
import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.topic.entity.Topic;
import com.dmu.dshorts.global.common.Ids;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** DTOs of {@code /api/v1/admin/personas}. */
public final class AdminPersonaDtos {

    private AdminPersonaDtos() {
    }

    static List<AdminTopicRef> topicRefs(Persona persona) {
        return persona.getTopics().stream()
                .sorted(Comparator.comparingInt(Topic::getSortOrder).thenComparing(Topic::getId))
                .map(AdminTopicRef::from)
                .toList();
    }

    /** List row: profile and state, without the internal AI settings. */
    public record AdminPersonaSummary(String id, String username, String displayName, String era,
                                      String avatarPath, String avatarUrl, boolean active, boolean deleted,
                                      Instant deletedAt, Instant updatedAt, List<AdminTopicRef> topics) {

        public static AdminPersonaSummary from(Persona p) {
            return new AdminPersonaSummary(Ids.str(p.getId()), p.getUsername(), p.getDisplayName(), p.getEra(),
                    p.getAvatarPath(), MediaStorage.urlOf(p.getAvatarPath()), p.isActive(), p.getDeletedAt() != null,
                    p.getDeletedAt(), p.getUpdatedAt(), topicRefs(p));
        }
    }

    /** Full admin view including perspective/voice/knowledgeScope/prohibitions/conversationRules. */
    public record AdminPersonaDetail(String id, String username, String displayName, String era, String bio,
                                     String avatarPath, String avatarUrl, String aiDisclosure,
                                     String perspective, String voice, String knowledgeScope,
                                     String prohibitions, String conversationRules,
                                     boolean active, boolean deleted, Instant deletedAt, Instant updatedAt,
                                     List<AdminTopicRef> topics) {

        public static AdminPersonaDetail from(Persona p) {
            return new AdminPersonaDetail(Ids.str(p.getId()), p.getUsername(), p.getDisplayName(), p.getEra(),
                    p.getBio(), p.getAvatarPath(), MediaStorage.urlOf(p.getAvatarPath()), p.getAiDisclosure(),
                    p.getPerspective(), p.getVoice(), p.getKnowledgeScope(), p.getProhibitions(),
                    p.getConversationRules(), p.isActive(), p.getDeletedAt() != null, p.getDeletedAt(),
                    p.getUpdatedAt(), topicRefs(p));
        }
    }

    /**
     * Create / update body. On create {@code username} and {@code displayName} are required; on update every
     * field is optional ({@code null} = unchanged, {@code topicIds} replaces the whole list,
     * {@code ""} clears {@code era}/{@code avatarPath}). {@code avatarPath} must be an uploaded image path.
     */
    public record PersonaRequest(String username, String displayName, String era, String bio, String avatarPath,
                                 String aiDisclosure, String perspective, String voice, String knowledgeScope,
                                 String prohibitions, String conversationRules, List<String> topicIds,
                                 Boolean active) {
    }
}
