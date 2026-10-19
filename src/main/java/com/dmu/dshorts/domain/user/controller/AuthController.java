package com.dmu.dshorts.domain.user.controller;

import com.dmu.dshorts.domain.user.dto.CsrfTokenResponse;
import com.dmu.dshorts.domain.user.dto.LoginRequest;
import com.dmu.dshorts.domain.user.dto.MyProfileResponse;
import com.dmu.dshorts.domain.user.entity.User;
import com.dmu.dshorts.domain.user.service.AuthService;
import com.dmu.dshorts.domain.user.service.UserService;
import com.dmu.dshorts.global.common.ApiResponse;
import com.dmu.dshorts.global.security.AuthUser;
import com.dmu.dshorts.global.security.CurrentUser;
import com.dmu.dshorts.global.security.SessionAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final SessionAuthenticator sessionAuthenticator;

    public AuthController(AuthService authService, UserService userService,
                          SessionAuthenticator sessionAuthenticator) {
        this.authService = authService;
        this.userService = userService;
        this.sessionAuthenticator = sessionAuthenticator;
    }

    /** Returns the CSRF token (also set as the XSRF-TOKEN cookie). Call once before the first POST. */
    @GetMapping("/csrf")
    public ApiResponse<CsrfTokenResponse> csrf(CsrfToken csrfToken) {
        return ApiResponse.of(new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken()));
    }

    @PostMapping("/login")
    public ApiResponse<MyProfileResponse> login(@Valid @RequestBody LoginRequest request,
                                                HttpServletRequest httpRequest,
                                                HttpServletResponse httpResponse) {
        User user = authService.authenticate(request.username(), request.password());
        sessionAuthenticator.signIn(AuthUser.from(user), httpRequest, httpResponse);
        return ApiResponse.of(userService.getProfile(user.getId()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CurrentUser(required = true) AuthUser user,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        sessionAuthenticator.signOut(httpRequest, httpResponse);
        return ResponseEntity.noContent().build();
    }
}
