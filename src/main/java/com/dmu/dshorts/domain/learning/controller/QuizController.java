package com.dmu.dshorts.domain.learning.controller;

import com.dmu.dshorts.domain.learning.dto.QuizResponse;
import com.dmu.dshorts.domain.learning.dto.QuizSubmitRequest;
import com.dmu.dshorts.domain.learning.dto.QuizSubmitResponse;
import com.dmu.dshorts.domain.learning.service.QuizService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.security.AuthUser;
import com.dmu.dshorts.global.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public post quiz: read without answers, submit for server grading (members are recorded). */
@RestController
@RequestMapping("/api/v1/posts/{postId}/quiz")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping
    public ApiResponse<QuizResponse> getQuiz(@PathVariable Long postId) {
        return ApiResponse.of(quizService.getQuiz(postId));
    }

    @PostMapping("/attempts")
    public ApiResponse<QuizSubmitResponse> submit(@PathVariable Long postId,
                                                  @RequestBody QuizSubmitRequest request,
                                                  @CurrentUser AuthUser user) {
        return ApiResponse.of(quizService.submit(postId, request, user));
    }
}
