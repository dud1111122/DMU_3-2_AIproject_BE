package com.dmu.dshorts.domain.user.dto;

import com.dmu.dshorts.domain.user.entity.User;
import com.dmu.dshorts.global.common.Ids;
import java.time.Instant;
import java.util.List;

/**
 * The logged-in user's own profile. {@code role} is for UI hints only; the server checks roles itself.
 * {@code interestTopicIds} contains only non-deleted topics, ordered by sortOrder, id.
 */
public record MyProfileResponse(
        String id,
        String username,
        String displayName,
        String bio,
        String avatarUrl,
        String role,
        List<String> interestTopicIds,
        Instant createdAt) {

    public static MyProfileResponse of(User user, List<Long> interestTopicIds) {
        return new MyProfileResponse(
                Ids.str(user.getId()),
                user.getUsername(),
                user.getDisplayName(),
                user.getBio(),
                user.getAvatarPath(),
                user.getRole().name(),
                Ids.strs(interestTopicIds),
                user.getCreatedAt());
    }
}
