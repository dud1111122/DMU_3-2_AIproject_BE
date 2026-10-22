package com.dmu.dshorts.domain.learning.controller;

import com.dmu.dshorts.domain.learning.dto.QuizAttemptDetail;
import com.dmu.dshorts.domain.learning.dto.QuizAttemptSummary;
import com.dmu.dshorts.domain.learning.dto.ReviewItemResponse;
import com.dmu.dshorts.domain.learning.dto.ReviewStatusUpdateRequest;
import com.dmu.dshorts.domain.learning.service.QuizService;
import com.dmu.dshorts.domain.learning.service.ReviewItemService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.PageResponse;
import com.dmu.dshorts.global.security.AuthUser;
import com.dmu.dshorts.global.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Member quiz history and review items. */
@RestController
@RequestMapping("/api/v1/users/me")
public class MyLearningController {

    private final QuizService quizService;
    private final ReviewItemService reviewItemService;

    public MyLearningController(QuizService quizService, ReviewItemService reviewItemService) {
        this.quizService = quizService;
        this.reviewItemService = reviewItemService;
    }

    @GetMapping("/quiz-attempts")
    public ApiResponse<PageResponse<QuizAttemptSummary>> attempts(@CurrentUser(required = true) AuthUser user,
                                                                  @RequestParam(required = false) String cursor,
                                                                  @RequestParam(required = false) Integer limit) {
        return ApiResponse.of(quizService.listMyAttempts(user.id(), cursor, limit));
    }

    @GetMapping("/quiz-attempts/{attemptId}")
    public ApiResponse<QuizAttemptDetail> attempt(@CurrentUser(required = true) AuthUser user,
                                                  @PathVariable Long attemptId) {
        return ApiResponse.of(quizService.getMyAttempt(user.id(), attemptId));
    }

    @GetMapping("/review-items")
    public ApiResponse<PageResponse<ReviewItemResponse>> reviewItems(@CurrentUser(required = true) AuthUser user,
                                                                     @RequestParam(required = false) String status,
                                                                     @RequestParam(required = false) String cursor,
                                                                     @RequestParam(required = false) Integer limit) {
        return ApiResponse.of(reviewItemService.listMine(user.id(), status, cursor, limit));
    }

    @PatchMapping("/review-items/{reviewItemId}")
    public ApiResponse<ReviewItemResponse> updateReviewItem(@CurrentUser(required = true) AuthUser user,
                                                            @PathVariable Long reviewItemId,
                                                            @RequestBody ReviewStatusUpdateRequest request) {
        return ApiResponse.of(reviewItemService.updateStatus(user.id(), reviewItemId,
                request == null ? null : request.status()));
    }
}
