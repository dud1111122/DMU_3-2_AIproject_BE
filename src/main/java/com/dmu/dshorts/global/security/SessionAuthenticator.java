package com.dmu.dshorts.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/** Stores/removes the authenticated principal in the server-side HttpSession. Used by the auth endpoints only. */
@Component
public class SessionAuthenticator {

    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;

    public SessionAuthenticator(SecurityContextRepository securityContextRepository,
                                CsrfTokenRepository csrfTokenRepository) {
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    /**
     * Logs {@code user} in: new session ID (fixation protection), principal saved in the session,
     * and a fresh CSRF token cookie.
     */
    public void signIn(AuthUser user, HttpServletRequest request, HttpServletResponse response) {
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            request.changeSessionId();
        } else {
            request.getSession(true);
        }
        storePrincipal(user, request, response);
        CsrfToken rotated = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(rotated, request, response);
    }

    /** Replaces the principal of the current session (e.g. after the username changed). */
    public void refresh(AuthUser user, HttpServletRequest request, HttpServletResponse response) {
        storePrincipal(user, request, response);
    }

    /** Invalidates the session and clears the security context. */
    public void signOut(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    private void storePrincipal(AuthUser user, HttpServletRequest request, HttpServletResponse response) {
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
