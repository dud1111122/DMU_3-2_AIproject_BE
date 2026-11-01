package com.dmu.dshorts.domain.persona.dto;

import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.post.dto.MediaUrls;
import com.dmu.dshorts.domain.topic.dto.TopicSummary;
import com.dmu.dshorts.global.common.Ids;

import java.util.List;

/** Persona item of {@code /personas}, persona search and {@code /users/me/follows}. No internal prompt fields. */
public record PersonaListItem(String id, String username, String displayName, String era, String bio,
                              String avatarUrl, boolean aiPersona, List<TopicSummary> topics) {

    public static PersonaListItem from(Persona persona, List<TopicSummary> topics) {
        return new PersonaListItem(Ids.str(persona.getId()), persona.getUsername(), persona.getDisplayName(),
                persona.getEra(), persona.getBio(), MediaUrls.of(persona.getAvatarPath()), true, topics);
    }
}
