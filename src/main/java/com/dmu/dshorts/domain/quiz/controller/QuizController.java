package com.dmu.dshorts.domain.quiz.controller;

import com.dmu.dshorts.domain.quiz.dto.QuizAttemptRequest;
import com.dmu.dshorts.domain.quiz.dto.QuizResponse;
import com.dmu.dshorts.domain.quiz.service.QuizService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.UserIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class QuizController {
    private final QuizService quizzes;private final UserIdentity identity;
    public QuizController(QuizService quizzes,UserIdentity identity){this.quizzes=quizzes;this.identity=identity;}
    @GetMapping("/posts/{postId}/quiz") public ApiResponse<QuizResponse> byPost(@PathVariable String postId){return ApiResponse.of(quizzes.byPost(postId));}
    @GetMapping("/content-batches/{batchId}/quiz") public ApiResponse<QuizResponse> byBatch(@PathVariable String batchId){return ApiResponse.of(quizzes.byBatch(batchId));}
    @PostMapping("/quizzes/{quizId}/attempts") public ApiResponse<Map<String,Object>> submit(@PathVariable String quizId,@Valid @RequestBody QuizAttemptRequest body,HttpServletRequest req){return ApiResponse.of(quizzes.submit(quizId,identity.current(req),body));}
}
