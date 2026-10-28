package com.truthguard.backend.controller;

import com.truthguard.backend.dto.AccountExportResponse;
import com.truthguard.backend.dto.AuthResponse;
import com.truthguard.backend.dto.DeleteAccountRequest;
import com.truthguard.backend.dto.LoginRequest;
import com.truthguard.backend.dto.MeResponse;
import com.truthguard.backend.dto.RegisterRequest;
import com.truthguard.backend.model.User;
import com.truthguard.backend.service.AuditLogService;
import com.truthguard.backend.service.AuthService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;
    private final AuditLogService auditLogService;

    public AuthController(AuthService authService, AuditLogService auditLogService) {
        this.authService = authService;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        auditLogService.log(response.userId(), "REGISTER", "/auth/register", 200, Map.of());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        auditLogService.log(response.userId(), "LOGIN", "/auth/login", 200, Map.of());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            authService.logout(authorization.substring(7));
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(authService.me(user));
    }

    @GetMapping("/me/export")
    public ResponseEntity<AccountExportResponse> export(@AuthenticationPrincipal User user) {
        AccountExportResponse export = authService.export(user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"truthguard-export.json\"")
                .body(export);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody DeleteAccountRequest request
    ) {
        authService.deleteAccount(user, request.password());
        log.info("Account deleted for userId={}", user.getId());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
