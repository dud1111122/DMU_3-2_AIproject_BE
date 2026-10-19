package com.dmu.dshorts.global.security;

import com.dmu.dshorts.domain.user.entity.User;
import com.dmu.dshorts.domain.user.entity.UserRole;
import java.io.Serializable;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The logged-in account as stored in the HttpSession (principal of the Authentication).
 * Only {@code id} and {@code role} are authoritative; load the {@link User} row when you need profile data.
 */
public record AuthUser(Long id, String username, UserRole role) implements Serializable {

    public static AuthUser from(User user) {
        return new AuthUser(user.getId(), user.getUsername(), user.getRole());
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
