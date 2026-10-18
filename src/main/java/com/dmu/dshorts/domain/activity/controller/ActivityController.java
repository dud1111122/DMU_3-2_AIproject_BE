package com.dmu.dshorts.domain.activity.controller;

import com.dmu.dshorts.domain.user.service.UserService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.UserIdentity;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/users/me")
public class ActivityController {
    private final UserService users;private final UserIdentity identity;
    public ActivityController(UserService users,UserIdentity identity){this.users=users;this.identity=identity;}
    @PutMapping("/follows/{personaId}") public ApiResponse<Map<String,Object>> follow(@PathVariable String personaId,HttpServletRequest req){return ApiResponse.of(users.activity(identity.current(req),"FOLLOW",personaId,true));}
    @DeleteMapping("/follows/{personaId}") public ApiResponse<Map<String,Object>> unfollow(@PathVariable String personaId,HttpServletRequest req){return ApiResponse.of(users.activity(identity.current(req),"FOLLOW",personaId,false));}
    @PutMapping("/posts/{postId}/like") public ApiResponse<Map<String,Object>> like(@PathVariable String postId,HttpServletRequest req){return ApiResponse.of(users.activity(identity.current(req),"LIKE",postId,true));}
    @DeleteMapping("/posts/{postId}/like") public ApiResponse<Map<String,Object>> unlike(@PathVariable String postId,HttpServletRequest req){return ApiResponse.of(users.activity(identity.current(req),"LIKE",postId,false));}
    @PutMapping("/saved-posts/{postId}") public ApiResponse<Map<String,Object>> save(@PathVariable String postId,HttpServletRequest req){return ApiResponse.of(users.activity(identity.current(req),"SAVE",postId,true));}
    @DeleteMapping("/saved-posts/{postId}") public ApiResponse<Map<String,Object>> unsave(@PathVariable String postId,HttpServletRequest req){return ApiResponse.of(users.activity(identity.current(req),"SAVE",postId,false));}
}
