package com.truthguard.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record FeedbackRequest(
        @NotBlank String analysisId,
        boolean correct,
        String correctedLabel,
        String comment
) {}
