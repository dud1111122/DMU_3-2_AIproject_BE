package com.dmu.dshorts.domain.user.service;

import com.dmu.dshorts.domain.user.entity.User;
import com.dmu.dshorts.domain.user.repository.UserRepository;
import com.dmu.dshorts.global.error.ApiException;
import com.dmu.dshorts.global.error.ErrorCode;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    static final String INVALID_CREDENTIALS = "아이디 또는 비밀번호가 올바르지 않습니다.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-password");
    }

    /** Verifies credentials. Unknown user and wrong password produce the same 401 (and similar timing). */
    @Transactional(readOnly = true)
    public User authenticate(String username, String password) {
        Optional<User> user = userRepository.findByUsername(username.trim().toLowerCase(Locale.ROOT));
        if (user.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(password, user.get().getPasswordHash())) {
            throw invalidCredentials();
        }
        return user.get();
    }

    private static ApiException invalidCredentials() {
        return new ApiException(ErrorCode.UNAUTHORIZED, INVALID_CREDENTIALS);
    }
}
