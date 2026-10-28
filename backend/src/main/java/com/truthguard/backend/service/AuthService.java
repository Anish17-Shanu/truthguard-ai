package com.truthguard.backend.service;

import com.truthguard.backend.dto.AccountExportResponse;
import com.truthguard.backend.dto.AuthResponse;
import com.truthguard.backend.dto.LoginRequest;
import com.truthguard.backend.dto.MeResponse;
import com.truthguard.backend.dto.RegisterRequest;
import com.truthguard.backend.exception.ApiException;
import com.truthguard.backend.model.AnalysisRecord;
import com.truthguard.backend.model.FeedbackRecord;
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
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthService {
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final AnalysisRepository analysisRepository;
    private final FeedbackRepository feedbackRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final AiClientService aiClientService;

    public AuthService(
            UserRepository userRepository,
            SessionRepository sessionRepository,
            AnalysisRepository analysisRepository,
            FeedbackRepository feedbackRepository,
            AuditLogRepository auditLogRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginAttemptService loginAttemptService,
            AiClientService aiClientService
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.analysisRepository = analysisRepository;
        this.feedbackRepository = feedbackRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.aiClientService = aiClientService;
    }

    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already in use");
        }

        User user = User.builder()
                .fullName(request.fullName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .createdAt(Instant.now())
                .build();
        user = userRepository.save(user);
        return issueToken(user);
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        loginAttemptService.assertAllowed(email);
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(email);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        loginAttemptService.recordSuccess(email);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        return issueToken(user);
    }

    public void logout(String token) {
        try {
            Claims claims = jwtService.parse(token);
            if (claims.getId() != null) {
                sessionRepository.deleteById(claims.getId());
            }
        } catch (Exception ex) {
            log.debug("Ignoring invalid token during logout");
        }
    }

    public MeResponse me(User user) {
        return new MeResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }

    public AccountExportResponse export(User user) {
        List<AnalysisRecord> analyses = analysisRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(user.getId(), Instant.EPOCH);
        List<FeedbackRecord> feedback = feedbackRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return new AccountExportResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                user.getCreatedAt(),
                analyses,
                feedback);
    }

    public void deleteAccount(User user, String password) {
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Password confirmation failed");
        }
        analysisRepository.deleteByUserId(user.getId());
        feedbackRepository.deleteByUserId(user.getId());
        sessionRepository.deleteByUserId(user.getId());
        auditLogRepository.deleteByUserId(user.getId());
        userRepository.deleteById(user.getId());
        try {
            aiClientService.deleteUserData(user.getId());
        } catch (Exception ex) {
            log.warn("Could not delete AI-service data for user {}: {}", user.getId(), ex.getMessage());
        }
    }

    private AuthResponse issueToken(User user) {
        JwtService.IssuedToken issued = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        sessionRepository.save(Session.builder()
                .id(issued.tokenId())
                .userId(user.getId())
                .createdAt(Instant.now())
                .expiresAt(issued.expiresAt())
                .build());
        return new AuthResponse(
                issued.token(),
                jwtService.expiresInSeconds(),
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole());
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
