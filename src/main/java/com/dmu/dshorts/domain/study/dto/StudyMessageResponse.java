package com.dmu.dshorts.domain.study.dto;

import com.dmu.dshorts.domain.study.entity.ConversationMessage;
import com.dmu.dshorts.domain.study.entity.MessageRole;
import com.dmu.dshorts.global.common.Ids;
import java.time.Instant;

/** One stored chat message. {@code aiGenerated} is true for every PERSONA message (AI recreation). */
public record StudyMessageResponse(
        String id,
        MessageRole role,
        String content,
        boolean aiGenerated,
        Instant createdAt
) {
    public static StudyMessageResponse from(ConversationMessage message) {
        return new StudyMessageResponse(Ids.str(message.getId()), message.getRole(), message.getContent(),
                message.getRole() == MessageRole.PERSONA, message.getCreatedAt());
    }
}
