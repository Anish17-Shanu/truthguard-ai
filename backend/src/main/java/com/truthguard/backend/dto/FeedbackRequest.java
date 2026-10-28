package com.truthguard.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FeedbackRequest(
        @NotBlank String analysisId,
        boolean correct,
        @Pattern(regexp = "(?i)real|fake|propaganda|uncertain", message = "Unsupported label")
        String correctedLabel,
        @Size(max = 1000) String comment
) {}
