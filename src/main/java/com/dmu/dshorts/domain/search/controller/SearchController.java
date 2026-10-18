package com.dmu.dshorts.domain.search.controller;

import com.dmu.dshorts.domain.post.service.CatalogService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.UserIdentity;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/search")
public class SearchController {
    private final CatalogService catalog;private final UserIdentity identity;
    public SearchController(CatalogService catalog,UserIdentity identity){this.catalog=catalog;this.identity=identity;}
    @GetMapping @Operation(summary="페르소나·주제·게시물 통합 검색")
    public ApiResponse<com.dmu.dshorts.global.common.PageResponse<Map<String,Object>>> search(@RequestParam String q,@RequestParam(defaultValue="all") String type,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int limit,HttpServletRequest request){return ApiResponse.of(catalog.search(q,type,cursor,limit,identity.current(request)));}
}
