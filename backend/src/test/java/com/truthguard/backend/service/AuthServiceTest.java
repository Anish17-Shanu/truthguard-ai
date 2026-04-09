package com.truthguard.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.truthguard.backend.dto.LoginRequest;
import com.truthguard.backend.dto.RegisterRequest;
import com.truthguard.backend.exception.ApiException;
import com.truthguard.backend.model.User;
import com.truthguard.backend.repository.UserRepository;
import com.truthguard.backend.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setup() {
        userRepository = Mockito.mock(UserRepository.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        jwtService = Mockito.mock(JwtService.class);
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(true);
        assertThrows(ApiException.class, () -> authService.register(new RegisterRequest("A", "test@example.com", "password123")));
    }

    @Test
    void loginReturnsTokenWhenPasswordMatches() {
        User user = User.builder().id("u1").email("a@b.com").passwordHash("hash").build();
        when(userRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hash")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtService.generateToken(any(), any(), any())).thenReturn("token");
        when(jwtService.expiresInSeconds()).thenReturn(120L);

        var response = authService.login(new LoginRequest("a@b.com", "password123"));
        assertEquals("token", response.accessToken());
    }
}
