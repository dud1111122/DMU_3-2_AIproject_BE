package com.dmu.dshorts.domain.user.controller;

import com.dmu.dshorts.domain.post.dto.PostSummaryResponse;
import com.dmu.dshorts.domain.user.dto.PreferenceUpdateRequest;
import com.dmu.dshorts.domain.user.service.UserService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.PageResponse;
import com.dmu.dshorts.global.common.UserIdentity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/users/me")
public class UserController {
    private final UserService users;private final UserIdentity identity;
    public UserController(UserService users,UserIdentity identity){this.users=users;this.identity=identity;}
    @GetMapping("/preferences") public ApiResponse<Map<String,Object>> preferences(HttpServletRequest req){return ApiResponse.of(users.preferences(identity.current(req)));}
    @PatchMapping("/preferences") public ApiResponse<Map<String,String>> update(@Valid @RequestBody PreferenceUpdateRequest body,HttpServletRequest req){return ApiResponse.of(users.updateMode(identity.current(req),body.mode()));}
    @PutMapping("/interests/{topicId}") public ApiResponse<Map<String,String>> addInterest(@PathVariable String topicId,HttpServletRequest req){return ApiResponse.of(users.interest(identity.current(req),topicId,true));}
    @DeleteMapping("/interests/{topicId}") public ApiResponse<Map<String,String>> removeInterest(@PathVariable String topicId,HttpServletRequest req){return ApiResponse.of(users.interest(identity.current(req),topicId,false));}
    @GetMapping("/saved-posts") public ApiResponse<PageResponse<PostSummaryResponse>> saved(@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int limit,HttpServletRequest req){return ApiResponse.of(users.saved(identity.current(req),cursor,limit));}
}
