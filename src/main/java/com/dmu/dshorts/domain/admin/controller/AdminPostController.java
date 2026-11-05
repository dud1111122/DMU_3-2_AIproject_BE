package com.dmu.dshorts.domain.admin.controller;

import com.dmu.dshorts.domain.admin.dto.AdminPostDtos.AdminPostDetail;
import com.dmu.dshorts.domain.admin.dto.AdminPostDtos.AdminPostSummary;
import com.dmu.dshorts.domain.admin.dto.AdminPostDtos.UpdatePostRequest;
import com.dmu.dshorts.domain.admin.dto.AdminPostDtos.VisibilityRequest;
import com.dmu.dshorts.domain.admin.service.AdminPostService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/posts")
public class AdminPostController {

    private final AdminPostService postService;

    public AdminPostController(AdminPostService postService) {
        this.postService = postService;
    }

    /** Newest first by {@code (publishedAt,id)}; hidden included, deleted only with {@code includeDeleted=true}. */
    @GetMapping
    public ApiResponse<PageResponse<AdminPostSummary>> list(@RequestParam(required = false) String status,
                                                            @RequestParam(defaultValue = "false") boolean includeDeleted,
                                                            @RequestParam(required = false) String cursor,
                                                            @RequestParam(required = false) Integer limit) {
        return ApiResponse.of(postService.list(status, includeDeleted, cursor, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminPostDetail> get(@PathVariable Long id) {
        return ApiResponse.of(postService.get(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<AdminPostDetail> update(@PathVariable Long id, @RequestBody UpdatePostRequest request) {
        return ApiResponse.of(postService.update(id, request));
    }

    @PatchMapping("/{id}/visibility")
    public ApiResponse<AdminPostSummary> visibility(@PathVariable Long id, @RequestBody VisibilityRequest request) {
        return ApiResponse.of(postService.changeVisibility(id, request.status()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
