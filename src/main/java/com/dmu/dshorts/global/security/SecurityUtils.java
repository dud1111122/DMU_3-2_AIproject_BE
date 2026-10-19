package com.dmu.dshorts.global.security;

import com.dmu.dshorts.global.error.ApiException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Static access to the current user for code that has no {@link CurrentUser} parameter (e.g. services).
 * Admin URLs ({@code /api/v1/admin/**}) are already restricted to ADMIN by {@link SecurityConfig}.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** The logged-in user, or empty for guests. */
    public static Optional<AuthUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /** The logged-in user, or 401 UNAUTHORIZED. */
    public static AuthUser requireUser() {
        return currentUser().orElseThrow(ApiException::unauthorized);
    }

    /** Returns {@code user} if not null, otherwise 401 UNAUTHORIZED. */
    public static AuthUser requireUser(AuthUser user) {
        if (user == null) {
            throw ApiException.unauthorized();
        }
        return user;
    }

    /** The logged-in ADMIN, 401 for guests, 403 FORBIDDEN for normal users. */
    public static AuthUser requireAdmin() {
        AuthUser user = requireUser();
        if (!user.isAdmin()) {
            throw ApiException.forbidden();
        }
        return user;
    }
}
