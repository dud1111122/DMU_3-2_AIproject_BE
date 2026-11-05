package com.dmu.dshorts.domain.admin.controller;

import com.dmu.dshorts.domain.admin.dto.AdminTopicDtos.AdminTopicResponse;
import com.dmu.dshorts.domain.admin.dto.AdminTopicDtos.CreateTopicRequest;
import com.dmu.dshorts.domain.admin.dto.AdminTopicDtos.UpdateTopicRequest;
import com.dmu.dshorts.domain.admin.service.AdminTopicService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/topics")
public class AdminTopicController {

    private final AdminTopicService topicService;

    public AdminTopicController(AdminTopicService topicService) {
        this.topicService = topicService;
    }

    /** All topics including deleted, {@code sortOrder,id} order, single page. */
    @GetMapping
    public ApiResponse<PageResponse<AdminTopicResponse>> list() {
        return ApiResponse.of(PageResponse.of(topicService.list(), null));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminTopicResponse> create(@Valid @RequestBody CreateTopicRequest request) {
        return ApiResponse.of(topicService.create(request));
    }

    @PatchMapping("/{id}")
    public ApiResponse<AdminTopicResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateTopicRequest request) {
        return ApiResponse.of(topicService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        topicService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
