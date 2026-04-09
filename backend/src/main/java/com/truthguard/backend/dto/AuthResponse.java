package com.truthguard.backend.dto;

import com.truthguard.backend.model.Role;

public record AuthResponse(
        String accessToken,
        long expiresIn,
        String userId,
        String fullName,
        String email,
        Role role
) {}
