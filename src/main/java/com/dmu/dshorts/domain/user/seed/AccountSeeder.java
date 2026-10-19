package com.dmu.dshorts.domain.user.seed;

import com.dmu.dshorts.domain.user.entity.User;
import com.dmu.dshorts.domain.user.entity.UserRole;
import com.dmu.dshorts.domain.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the two demo accounts (ADMIN and USER) on startup when absent. Credentials come only from
 * environment variables ADMIN_USERNAME/ADMIN_PASSWORD and DEMO_USERNAME/DEMO_PASSWORD.
 * Existing accounts are never modified.
 */
@Component
@Order(0)
public class AccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccountSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String demoUsername;
    private final String demoPassword;

    public AccountSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                         @Value("${app.seed.admin-username:}") String adminUsername,
                         @Value("${app.seed.admin-password:}") String adminPassword,
                         @Value("${app.seed.demo-username:}") String demoUsername,
                         @Value("${app.seed.demo-password:}") String demoPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.demoUsername = demoUsername;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed(adminUsername, adminPassword, "관리자", UserRole.ADMIN);
        seed(demoUsername, demoPassword, "시연 사용자", UserRole.USER);
    }

    private void seed(String username, String password, String displayName, UserRole role) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("Seed account for role {} skipped: username/password environment variables are not set", role);
            return;
        }
        if (userRepository.existsByUsername(username)) {
            return;
        }
        userRepository.save(new User(username, passwordEncoder.encode(password), displayName, role));
        log.info("Seed account created: {} ({})", username, role);
    }
}
