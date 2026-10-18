package com.dmu.dshorts.domain.post.controller;

import com.dmu.dshorts.domain.post.dto.PostDetailResponse;
import com.dmu.dshorts.domain.post.service.CatalogService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.UserIdentity;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/posts")
public class PostController {
    private final CatalogService catalog;private final UserIdentity identity;
    public PostController(CatalogService catalog,UserIdentity identity){this.catalog=catalog;this.identity=identity;}
    @GetMapping("/{id}") @Operation(summary="게시물 상세와 카드·학습 포인트·출처")
    public ApiResponse<PostDetailResponse> get(@PathVariable String id,HttpServletRequest request){return ApiResponse.of(catalog.post(id,identity.current(request)));}
}
