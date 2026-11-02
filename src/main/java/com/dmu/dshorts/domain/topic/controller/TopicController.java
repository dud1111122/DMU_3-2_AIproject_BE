package com.dmu.dshorts.domain.topic.controller;

import com.dmu.dshorts.domain.topic.dto.TopicResponse;
import com.dmu.dshorts.domain.topic.service.TopicService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/topics")
public class TopicController {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    /** Non-deleted topics (sortOrder, id). Not paginated: {@code nextCursor} is always null. */
    @GetMapping
    public ApiResponse<PageResponse<TopicResponse>> list() {
        return ApiResponse.of(PageResponse.of(topicService.listPublic(), null));
    }
}
