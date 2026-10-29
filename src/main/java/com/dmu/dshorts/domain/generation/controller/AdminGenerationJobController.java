package com.dmu.dshorts.domain.generation.controller;

import com.dmu.dshorts.domain.generation.dto.GenerationJobResponse;
import com.dmu.dshorts.domain.generation.service.GenerationJobQueryService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only (SecurityConfig protects /api/v1/admin/**): status of the preparation jobs. */
@RestController
@RequestMapping("/api/v1/admin/generation-jobs")
public class AdminGenerationJobController {

    private final GenerationJobQueryService queryService;

    public AdminGenerationJobController(GenerationJobQueryService queryService) {
        this.queryService = queryService;
    }

    /** {@code status} = PENDING/RUNNING/SUCCEEDED/FAILED (optional); newest job first. */
    @GetMapping
    public ApiResponse<PageResponse<GenerationJobResponse>> list(@RequestParam(required = false) String status,
                                                                 @RequestParam(required = false) String cursor,
                                                                 @RequestParam(required = false) Integer limit) {
        return ApiResponse.of(queryService.list(status, cursor, limit));
    }
}
