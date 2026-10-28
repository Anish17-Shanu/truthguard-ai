package com.truthguard.backend.dto;

import java.time.Instant;

public record FeedbackView(
        String id,
        String analysisId,
        String inputSnippet,
        String predictedLabel,
        double confidence,
        boolean correct,
        String correctedLabel,
        String comment,
        String modelVersion,
        Instant createdAt
) {}
