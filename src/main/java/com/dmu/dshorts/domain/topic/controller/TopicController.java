package com.dmu.dshorts.domain.topic.controller;

import com.dmu.dshorts.domain.post.service.CatalogService;
import com.dmu.dshorts.domain.topic.dto.TopicResponse;
import com.dmu.dshorts.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/topics")
public class TopicController {
    private final CatalogService catalog;
    public TopicController(CatalogService catalog){this.catalog=catalog;}
    @GetMapping @Operation(summary="주제 목록")
    public ApiResponse<List<TopicResponse>> list(){return ApiResponse.of(catalog.topics());}
}
