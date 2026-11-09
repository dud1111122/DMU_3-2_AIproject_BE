package com.dmu.dshorts.domain.study.controller;

import com.dmu.dshorts.domain.study.dto.AssessmentResponse;
import com.dmu.dshorts.domain.study.dto.SendMessageRequest;
import com.dmu.dshorts.domain.study.dto.SendMessageResponse;
import com.dmu.dshorts.domain.study.dto.StudyConversationResponse;
import com.dmu.dshorts.domain.study.service.StudyConversationService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.security.AuthUser;
import com.dmu.dshorts.global.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Member post study chat (BE_Endpoint §4). */
@RestController
@RequestMapping("/api/v1")
public class StudyConversationController {

    private final StudyConversationService conversationService;

    public StudyConversationController(StudyConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /** Reuses the user's room for the post: 201 when created, 200 when it already existed. */
    @PostMapping("/posts/{postId}/study-conversations")
    public ResponseEntity<ApiResponse<StudyConversationResponse>> open(@PathVariable Long postId,
                                                                       @CurrentUser(required = true) AuthUser user) {
        StudyConversationService.OpenResult result = conversationService.open(postId, user);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.of(result.conversation()));
    }

    @GetMapping("/study-conversations/{id}")
    public ApiResponse<StudyConversationResponse> get(@PathVariable Long id,
                                                      @RequestParam(required = false) Long beforeMessageId,
                                                      @RequestParam(required = false) Integer limit,
                                                      @CurrentUser(required = true) AuthUser user) {
        return ApiResponse.of(conversationService.get(id, user, beforeMessageId, limit));
    }

    @PostMapping("/study-conversations/{id}/messages")
    public ApiResponse<SendMessageResponse> send(@PathVariable Long id,
                                                 @Valid @RequestBody SendMessageRequest request,
                                                 @CurrentUser(required = true) AuthUser user) {
        return ApiResponse.of(conversationService.sendMessage(id, user, request.content()));
    }

    @GetMapping("/study-conversations/{id}/assessment")
    public ApiResponse<AssessmentResponse> assessment(@PathVariable Long id,
                                                      @CurrentUser(required = true) AuthUser user) {
        return ApiResponse.of(conversationService.assessment(id, user));
    }
}
