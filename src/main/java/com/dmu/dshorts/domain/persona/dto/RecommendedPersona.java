package com.dmu.dshorts.domain.persona.dto;

import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.post.dto.MediaUrls;
import com.dmu.dshorts.domain.topic.dto.TopicSummary;
import com.dmu.dshorts.global.common.Ids;

import java.util.List;

/** Item of {@code /personas/recommendations}: list item plus the number of topics matching the interests. */
public record RecommendedPersona(String id, String username, String displayName, String era, String bio,
                                 String avatarUrl, boolean aiPersona, List<TopicSummary> topics,
                                 int matchedTopicCount) {

    public static RecommendedPersona from(Persona persona, List<TopicSummary> topics, int matchedTopicCount) {
        return new RecommendedPersona(Ids.str(persona.getId()), persona.getUsername(), persona.getDisplayName(),
                persona.getEra(), persona.getBio(), MediaUrls.of(persona.getAvatarPath()), true, topics,
                matchedTopicCount);
    }
}
