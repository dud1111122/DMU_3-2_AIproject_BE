package com.dmu.dshorts.domain.persona.dto;

import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.post.dto.MediaUrls;
import com.dmu.dshorts.domain.topic.dto.TopicSummary;
import com.dmu.dshorts.global.common.Ids;

import java.util.List;

/**
 * {@code GET /personas/{id}}: public profile, public topics, AI disclosure, counts and the viewer's follow state
 * ({@code authenticated=false, followed=false} for guests). Internal prompt settings are never included.
 */
public record PersonaDetail(String id, String username, String displayName, String era, String bio,
                            String avatarUrl, boolean aiPersona, String aiDisclosure, List<TopicSummary> topics,
                            long postCount, long followerCount, boolean authenticated, boolean followed) {

    public static PersonaDetail from(Persona persona, List<TopicSummary> topics, long postCount, long followerCount,
                                     boolean authenticated, boolean followed) {
        return new PersonaDetail(Ids.str(persona.getId()), persona.getUsername(), persona.getDisplayName(),
                persona.getEra(), persona.getBio(), MediaUrls.of(persona.getAvatarPath()), true,
                persona.getAiDisclosure(), topics, postCount, followerCount, authenticated, followed);
    }
}
