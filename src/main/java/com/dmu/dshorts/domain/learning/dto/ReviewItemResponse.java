package com.dmu.dshorts.domain.learning.dto;

import com.dmu.dshorts.domain.learning.entity.ReviewReason;
import com.dmu.dshorts.domain.learning.entity.ReviewStatus;
import com.dmu.dshorts.domain.learning.repository.ReviewItemRepository.ReviewRow;
import com.dmu.dshorts.global.common.Ids;
import java.time.Instant;

/** Item of {@code GET /users/me/review-items} and body of {@code PATCH /users/me/review-items/{id}}. */
public record ReviewItemResponse(String id, String postId, String postTitle, String keyPointId, int keyPointOrder,
                                 String keyPointText, ReviewReason reason, ReviewStatus status, Instant updatedAt,
                                 Instant completedAt) {

    public static ReviewItemResponse from(ReviewRow row) {
        return new ReviewItemResponse(Ids.str(row.getId()), Ids.str(row.getPostId()), row.getPostTitle(),
                Ids.str(row.getKeyPointId()), row.getKeyPointOrder(), row.getKeyPointText(), row.getReason(),
                row.getStatus(), row.getUpdatedAt(), row.getCompletedAt());
    }
}
