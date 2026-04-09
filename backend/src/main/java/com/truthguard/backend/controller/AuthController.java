package com.truthguard.backend.controller;

import com.truthguard.backend.dto.AuthResponse;
import com.truthguard.backend.dto.LoginRequest;
import com.truthguard.backend.dto.RegisterRequest;
import com.truthguard.backend.service.AuditLogService;
import com.truthguard.backend.service.AuthService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        auditLogService.log(response.userId(), "REGISTER", "/auth/register", 200, Map.of("email", response.email()));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        auditLogService.log(response.userId(), "LOGIN", "/auth/login", 200, Map.of("email", response.email()));
        return ResponseEntity.ok(response);
    }
}

