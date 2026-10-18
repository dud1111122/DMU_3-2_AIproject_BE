package com.dmu.dshorts.domain.feed.controller;

import com.dmu.dshorts.domain.post.dto.PostSummaryResponse;
import com.dmu.dshorts.domain.post.service.CatalogService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.PageResponse;
import com.dmu.dshorts.global.common.UserIdentity;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/feed")
public class FeedController {
    private final CatalogService catalog; private final UserIdentity identity;
    public FeedController(CatalogService catalog,UserIdentity identity){this.catalog=catalog;this.identity=identity;}
    @GetMapping @Operation(summary="공통 콘텐츠 피드")
    public ApiResponse<PageResponse<PostSummaryResponse>> feed(@RequestParam(required=false) String topicId,@RequestParam(defaultValue="false") boolean following,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int limit,HttpServletRequest request){
        return ApiResponse.of(catalog.feed(topicId,following,cursor,limit,identity.current(request)));
    }
}
