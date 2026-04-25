package com.truthguard.backend.config;

import com.truthguard.backend.model.Role;
import com.truthguard.backend.model.User;
import com.truthguard.backend.repository.UserRepository;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BootstrapAdminRunner implements CommandLineRunner {
    private final AppProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminRunner(AppProperties properties, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @SuppressWarnings("null")
    public void run(String... args) {
        String email = properties.getBootstrap().getAdminEmail();
        String password = properties.getBootstrap().getAdminPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }

        userRepository.save(User.builder()
                .fullName(properties.getBootstrap().getAdminName())
                .email(email.toLowerCase())
                .passwordHash(passwordEncoder.encode(password))
                .role(Role.ADMIN)
                .createdAt(Instant.now())
                .build());

        log.info("Bootstrapped admin account for {}", email);
    }
}
