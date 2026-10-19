package com.dmu.dshorts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Accounts are checked by AuthService; no in-memory default user.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DeShortsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeShortsApplication.class, args);
    }
}
