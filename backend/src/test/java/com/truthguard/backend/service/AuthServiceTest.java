package com.truthguard.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.truthguard.backend.dto.LoginRequest;
import com.truthguard.backend.dto.RegisterRequest;
import com.truthguard.backend.exception.ApiException;
import com.truthguard.backend.model.Role;
import com.truthguard.backend.model.Session;
import com.truthguard.backend.model.User;
import com.truthguard.backend.repository.AnalysisRepository;
import com.truthguard.backend.repository.AuditLogRepository;
import com.truthguard.backend.repository.FeedbackRepository;
import com.truthguard.backend.repository.SessionRepository;
import com.truthguard.backend.repository.UserRepository;
import com.truthguard.backend.security.JwtService;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {
    private UserRepository userRepository;
    private SessionRepository sessionRepository;
    private AnalysisRepository analysisRepository;
    private FeedbackRepository feedbackRepository;
    private AuditLogRepository auditLogRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private LoginAttemptService loginAttemptService;
    private AiClientService aiClientService;
    private AuthService authService;

    @BeforeEach
    void setup() {
        userRepository = Mockito.mock(UserRepository.class);
        sessionRepository = Mockito.mock(SessionRepository.class);
        analysisRepository = Mockito.mock(AnalysisRepository.class);
        feedbackRepository = Mockito.mock(FeedbackRepository.class);
        auditLogRepository = Mockito.mock(AuditLogRepository.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        jwtService = Mockito.mock(JwtService.class);
        loginAttemptService = Mockito.mock(LoginAttemptService.class);
        aiClientService = Mockito.mock(AiClientService.class);
        authService = new AuthService(
                userRepository,
                sessionRepository,
                analysisRepository,
                feedbackRepository,
                auditLogRepository,
                passwordEncoder,
                jwtService,
                loginAttemptService,
                aiClientService);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(true);
        assertThrows(ApiException.class, () -> authService.register(new RegisterRequest("A", "test@example.com", "password123")));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginReturnsTokenAndPersistsSession() {
        User user = User.builder().id("u1").email("a@b.com").passwordHash("hash").role(Role.USER).build();
        when(userRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hash")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(user);
        Instant expiry = Instant.now().plusSeconds(3600);
        when(jwtService.generateToken("u1", "a@b.com", "USER"))
                .thenReturn(new JwtService.IssuedToken("token", "jti-1", expiry));
        when(jwtService.expiresInSeconds()).thenReturn(3600L);

        var response = authService.login(new LoginRequest("a@b.com", "password123"));

        assertEquals("token", response.accessToken());
        verify(sessionRepository).save(any(Session.class));
    }

    @Test
    void loginRejectsWrongPasswordAndRecordsFailure() {
        when(userRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.empty());

        ApiException exception = assertThrows(
                ApiException.class,
                () -> authService.login(new LoginRequest("a@b.com", "password123")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        verify(loginAttemptService).recordFailure("a@b.com");
        verify(sessionRepository, never()).save(any(Session.class));
    }

    @Test
    void logoutRevokesTheSession() {
        Claims claims = Mockito.mock(Claims.class);
        when(claims.getId()).thenReturn("jti-1");
        when(jwtService.parse("token")).thenReturn(claims);

        authService.logout("token");

        verify(sessionRepository).deleteById("jti-1");
    }

    @Test
    void deleteAccountRemovesUserDataAfterPasswordConfirmation() {
        User user = User.builder().id("u1").email("a@b.com").passwordHash("hash").role(Role.USER).build();
        when(passwordEncoder.matches("password123", "hash")).thenReturn(true);

        authService.deleteAccount(user, "password123");

        verify(analysisRepository).deleteByUserId("u1");
        verify(feedbackRepository).deleteByUserId("u1");
        verify(sessionRepository).deleteByUserId("u1");
        verify(auditLogRepository).deleteByUserId("u1");
        verify(userRepository).deleteById("u1");
        verify(aiClientService).deleteUserData("u1");
    }

    @Test
    void deleteAccountRejectsWrongPassword() {
        User user = User.builder().id("u1").email("a@b.com").passwordHash("hash").role(Role.USER).build();
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(ApiException.class, () -> authService.deleteAccount(user, "wrong"));

        verify(userRepository, never()).deleteById(anyString());
    }
}
