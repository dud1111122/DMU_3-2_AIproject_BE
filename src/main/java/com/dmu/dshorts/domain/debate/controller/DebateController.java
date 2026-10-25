package com.dmu.dshorts.domain.debate.controller;

import com.dmu.dshorts.domain.debate.dto.DebateDtos.CreateDebateRequest;
import com.dmu.dshorts.domain.debate.dto.DebateDtos.DebateListResponse;
import com.dmu.dshorts.domain.debate.dto.DebateDtos.DebateResponse;
import com.dmu.dshorts.domain.debate.service.DebateService;
import com.dmu.dshorts.domain.debate.service.DebateService.CreateResult;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.security.AuthUser;
import com.dmu.dshorts.global.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DebateController {

    private final DebateService debateService;

    public DebateController(DebateService debateService) {
        this.debateService = debateService;
    }

    /** 201 new debate, 200 reused own result. */
    @PostMapping("/debates")
    public ResponseEntity<ApiResponse<DebateResponse>> create(@CurrentUser(required = true) AuthUser user,
                                                              @RequestBody CreateDebateRequest request) {
        CreateResult result = debateService.create(user.id(), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.of(result.debate()));
    }

    @GetMapping("/debates/{id}")
    public ApiResponse<DebateResponse> get(@CurrentUser(required = true) AuthUser user, @PathVariable Long id) {
        return ApiResponse.of(debateService.get(user.id(), id));
    }

    @GetMapping("/users/me/debates")
    public ApiResponse<DebateListResponse> list(@CurrentUser(required = true) AuthUser user,
                                                @RequestParam(required = false) String cursor,
                                                @RequestParam(required = false) Integer limit) {
        return ApiResponse.of(debateService.list(user.id(), cursor, limit));
    }

    /** Public: result only, nothing persisted. */
    @PostMapping("/guest/debates")
    public ApiResponse<DebateResponse> createGuest(@RequestBody CreateDebateRequest request) {
        return ApiResponse.of(debateService.createGuest(request));
    }
}
