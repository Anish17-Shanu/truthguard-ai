package com.truthguard.backend.dto;

import com.truthguard.backend.model.Role;
import java.time.Instant;

public record MeResponse(
        String userId,
        String fullName,
        String email,
        Role role,
        Instant createdAt
) {}
