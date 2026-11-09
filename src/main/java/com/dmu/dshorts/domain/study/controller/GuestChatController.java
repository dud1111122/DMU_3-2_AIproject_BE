package com.dmu.dshorts.domain.study.controller;

import com.dmu.dshorts.domain.study.dto.GuestChatRequest;
import com.dmu.dshorts.domain.study.dto.GuestChatResponse;
import com.dmu.dshorts.domain.study.service.GuestChatService;
import com.dmu.dshorts.global.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Guest post chat (public). Returns reply + assessments, stores nothing. */
@RestController
@RequestMapping("/api/v1/guest")
public class GuestChatController {

    private final GuestChatService guestChatService;

    public GuestChatController(GuestChatService guestChatService) {
        this.guestChatService = guestChatService;
    }

    @PostMapping("/posts/{postId}/chat")
    public ApiResponse<GuestChatResponse> chat(@PathVariable Long postId,
                                               @Valid @RequestBody GuestChatRequest request) {
        return ApiResponse.of(guestChatService.chat(postId, request));
    }
}
