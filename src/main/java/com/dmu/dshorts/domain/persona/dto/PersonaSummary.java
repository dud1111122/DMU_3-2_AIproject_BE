package com.dmu.dshorts.domain.persona.dto;

import com.dmu.dshorts.domain.persona.entity.Persona;
import com.dmu.dshorts.domain.post.dto.MediaUrls;
import com.dmu.dshorts.global.common.Ids;

/** Persona reference embedded in post DTOs. {@code aiPersona} is always true (AI reproduction badge). */
public record PersonaSummary(String id, String username, String displayName, String avatarUrl, boolean aiPersona) {

    public static PersonaSummary from(Persona persona) {
        return new PersonaSummary(Ids.str(persona.getId()), persona.getUsername(), persona.getDisplayName(),
                MediaUrls.of(persona.getAvatarPath()), true);
    }
}
