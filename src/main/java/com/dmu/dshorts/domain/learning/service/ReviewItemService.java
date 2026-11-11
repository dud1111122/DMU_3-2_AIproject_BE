package com.dmu.dshorts.domain.learning.service;

import com.dmu.dshorts.domain.learning.dto.ReviewItemResponse;
import com.dmu.dshorts.domain.learning.entity.ReviewItem;
import com.dmu.dshorts.domain.learning.entity.ReviewReason;
import com.dmu.dshorts.domain.learning.entity.ReviewStatus;
import com.dmu.dshorts.domain.learning.repository.ReviewItemRepository;
import com.dmu.dshorts.domain.learning.repository.ReviewItemRepository.ReviewRow;
import com.dmu.dshorts.global.common.Cursors;
import com.dmu.dshorts.global.common.Cursors.TimeIdCursor;
import com.dmu.dshorts.global.common.PageResponse;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.visibility.VisibilityService;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Review items ("복습"): open/reopen on learning misses, member list and manual status change. */
@Service
public class ReviewItemService {

    private final ReviewItemRepository reviewItemRepository;
    private final VisibilityService visibilityService;

    public ReviewItemService(ReviewItemRepository reviewItemRepository, VisibilityService visibilityService) {
        this.reviewItemRepository = reviewItemRepository;
        this.visibilityService = visibilityService;
    }

    /**
     * Opens or reopens the user's review item for a key point (one row per user and key point).
     * New row: OPEN with the given reason. Existing row: reason replaced, status OPEN, completed_at cleared,
     * updated_at = now(). Atomic ({@code INSERT ... ON CONFLICT}), so concurrent calls never duplicate rows.
     *
     * <p>Joins the caller's transaction (use it inside the quiz/chat save transaction). The caller must have
     * verified that {@code keyPointId} belongs to {@code postId} and that the post is visible; a mismatch
     * violates the composite FK and fails the transaction.
     */
    @Transactional
    public void openReview(Long userId, Long postId, Long keyPointId, ReviewReason reason) {
        reviewItemRepository.upsertOpen(userId, postId, keyPointId, reason.name(), Instant.now());
    }

    /** {@code GET /users/me/review-items}: updated_at desc, id desc; only items of currently visible posts. */
    @Transactional(readOnly = true)
    public PageResponse<ReviewItemResponse> listMine(Long userId, String statusParam, String cursor,
                                                     Integer limitParam) {
        Set<ReviewStatus> statuses = statusParam == null || statusParam.isBlank()
                ? EnumSet.allOf(ReviewStatus.class)
                : EnumSet.of(parseStatus(statusParam));
        int limit = Cursors.limit(limitParam);
        TimeIdCursor after = Cursors.decodeTimeId(cursor);
        PageRequest page = PageRequest.ofSize(limit + 1);
        List<ReviewRow> rows = after == null
                ? reviewItemRepository.findMyVisibleFirstPage(userId, statuses, page)
                : reviewItemRepository.findMyVisibleAfter(userId, statuses, after.time(), after.id(), page);
        return PageResponse.slice(rows, limit, row -> Cursors.encode(row.getUpdatedAt(), row.getId()),
                ReviewItemResponse::from);
    }

    /**
     * {@code PATCH /users/me/review-items/{id}}: DONE sets completed_at (kept if already DONE), OPEN clears it.
     * Other users' items and items of invisible posts → 404.
     */
    @Transactional
    public ReviewItemResponse updateStatus(Long userId, Long reviewItemId, String statusParam) {
        if (statusParam == null || statusParam.isBlank()) {
            throw ApiException.validation("status", "status는 DONE 또는 OPEN이어야 합니다.");
        }
        ReviewStatus target = parseStatus(statusParam);
        ReviewItem item = reviewItemRepository.findByIdAndUserId(reviewItemId, userId)
                .orElseThrow(ApiException::notFound);
        visibilityService.requireVisiblePost(item.getPostId());
        if (item.getStatus() != target) {
            item.setStatus(target);
            item.setCompletedAt(target == ReviewStatus.DONE ? Instant.now() : null);
            reviewItemRepository.saveAndFlush(item);
        }
        return reviewItemRepository.findMyVisibleRow(reviewItemId, userId)
                .map(ReviewItemResponse::from)
                .orElseThrow(ApiException::notFound);
    }

    private static ReviewStatus parseStatus(String raw) {
        try {
            return ReviewStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.validation("status", "status는 DONE 또는 OPEN이어야 합니다.");
        }
    }
}
