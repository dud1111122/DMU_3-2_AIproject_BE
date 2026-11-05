package com.dmu.dshorts.domain.admin.dto;

import com.dmu.dshorts.domain.topic.entity.Topic;
import com.dmu.dshorts.global.common.Ids;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** DTOs of {@code /api/v1/admin/topics}. */
public final class AdminTopicDtos {

    private AdminTopicDtos() {
    }

    public record AdminTopicResponse(String id, String name, String description, int sortOrder,
                                     boolean deleted, Instant deletedAt) {

        public static AdminTopicResponse from(Topic topic) {
            return new AdminTopicResponse(Ids.str(topic.getId()), topic.getName(), topic.getDescription(),
                    topic.getSortOrder(), topic.isDeleted(), topic.getDeletedAt());
        }
    }

    /** Topic reference inside admin persona/post DTOs; deleted links stay visible to admins (flagged). */
    public record AdminTopicRef(String id, String name, boolean deleted) {

        public static AdminTopicRef from(Topic topic) {
            return new AdminTopicRef(Ids.str(topic.getId()), topic.getName(), topic.isDeleted());
        }
    }

    public record CreateTopicRequest(
            @NotBlank(message = "이름은 필수입니다.") @Size(max = 50, message = "이름은 50자 이하여야 합니다.") String name,
            @Size(max = 300, message = "설명은 300자 이하여야 합니다.") String description,
            Integer sortOrder) {
    }

    /** All fields optional; {@code null} = unchanged. */
    public record UpdateTopicRequest(
            @Size(min = 1, max = 50, message = "이름은 1~50자여야 합니다.") String name,
            @Size(max = 300, message = "설명은 300자 이하여야 합니다.") String description,
            Integer sortOrder) {
    }
}
