package com.dmu.dshorts.domain.learning.controller;

import com.dmu.dshorts.domain.learning.service.ReviewService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.UserIdentity;
import com.dmu.dshorts.global.common.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/users/me/review-items")
public class ReviewController {
    private final ReviewService reviews;private final UserIdentity identity;
    public ReviewController(ReviewService reviews,UserIdentity identity){this.reviews=reviews;this.identity=identity;}
    @GetMapping public ApiResponse<PageResponse<Map<String,Object>>> list(@RequestParam(required=false) String status,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int limit,HttpServletRequest req){return ApiResponse.of(reviews.list(identity.current(req),status,cursor,limit));}
    @PatchMapping("/{id}") public ApiResponse<Map<String,Object>> complete(@PathVariable Long id,HttpServletRequest req){return ApiResponse.of(reviews.complete(identity.current(req),id));}
}
