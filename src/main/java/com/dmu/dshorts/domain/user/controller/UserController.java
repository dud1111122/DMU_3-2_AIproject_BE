package com.dmu.dshorts.domain.user.controller;

import com.dmu.dshorts.domain.user.dto.MyProfileResponse;
import com.dmu.dshorts.domain.user.dto.UpdateMeRequest;
import com.dmu.dshorts.domain.user.service.UserService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.security.AuthUser;
import com.dmu.dshorts.global.security.CurrentUser;
import com.dmu.dshorts.global.security.SessionAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;
    private final SessionAuthenticator sessionAuthenticator;

    public UserController(UserService userService, SessionAuthenticator sessionAuthenticator) {
        this.userService = userService;
        this.sessionAuthenticator = sessionAuthenticator;
    }

    @GetMapping
    public ApiResponse<MyProfileResponse> me(@CurrentUser(required = true) AuthUser user) {
        return ApiResponse.of(userService.getProfile(user.id()));
    }

    @PatchMapping
    public ApiResponse<MyProfileResponse> update(@CurrentUser(required = true) AuthUser user,
                                                 @Valid @RequestBody UpdateMeRequest request,
                                                 HttpServletRequest httpRequest,
                                                 HttpServletResponse httpResponse) {
        MyProfileResponse profile = userService.updateProfile(user.id(), request);
        if (!profile.username().equals(user.username())) {
            AuthUser refreshed = new AuthUser(user.id(), profile.username(), user.role());
            sessionAuthenticator.refresh(refreshed, httpRequest, httpResponse);
        }
        return ApiResponse.of(profile);
    }
}
