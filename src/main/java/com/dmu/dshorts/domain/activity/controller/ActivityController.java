package com.dmu.dshorts.domain.activity.controller;

import com.dmu.dshorts.domain.activity.dto.SavedPostItem;
import com.dmu.dshorts.domain.activity.service.ActivityService;
import com.dmu.dshorts.domain.persona.dto.PersonaListItem;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.common.PageResponse;
import com.dmu.dshorts.global.security.AuthUser;
import com.dmu.dshorts.global.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Member-only activity endpoints. All PUT/DELETE are idempotent and return 204. */
@RestController
@RequestMapping("/api/v1/users/me")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @PutMapping("/interests/{topicId}")
    public ResponseEntity<Void> addInterest(@PathVariable Long topicId, @CurrentUser(required = true) AuthUser user) {
        activityService.addInterest(user, topicId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/interests/{topicId}")
    public ResponseEntity<Void> removeInterest(@PathVariable Long topicId,
                                               @CurrentUser(required = true) AuthUser user) {
        activityService.removeInterest(user, topicId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/follows/{personaId}")
    public ResponseEntity<Void> follow(@PathVariable Long personaId, @CurrentUser(required = true) AuthUser user) {
        activityService.follow(user, personaId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/follows/{personaId}")
    public ResponseEntity<Void> unfollow(@PathVariable Long personaId, @CurrentUser(required = true) AuthUser user) {
        activityService.unfollow(user, personaId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/follows")
    public ApiResponse<PageResponse<PersonaListItem>> follows(@RequestParam(required = false) String cursor,
                                                              @RequestParam(required = false) Integer limit,
                                                              @CurrentUser(required = true) AuthUser user) {
        return ApiResponse.of(activityService.follows(user, cursor, limit));
    }

    @PutMapping("/posts/{postId}/like")
    public ResponseEntity<Void> like(@PathVariable Long postId, @CurrentUser(required = true) AuthUser user) {
        activityService.like(user, postId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/posts/{postId}/like")
    public ResponseEntity<Void> unlike(@PathVariable Long postId, @CurrentUser(required = true) AuthUser user) {
        activityService.unlike(user, postId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/saved-posts/{postId}")
    public ResponseEntity<Void> save(@PathVariable Long postId, @CurrentUser(required = true) AuthUser user) {
        activityService.save(user, postId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/saved-posts/{postId}")
    public ResponseEntity<Void> unsave(@PathVariable Long postId, @CurrentUser(required = true) AuthUser user) {
        activityService.unsave(user, postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/saved-posts")
    public ApiResponse<PageResponse<SavedPostItem>> savedPosts(@RequestParam(required = false) String cursor,
                                                               @RequestParam(required = false) Integer limit,
                                                               @CurrentUser(required = true) AuthUser user) {
        return ApiResponse.of(activityService.savedPosts(user, cursor, limit));
    }
}
