package com.truthguard.backend.dto;

import java.time.Instant;
import java.util.List;

public record AnalysisResponse(
        String analysisId,
        String sourceType,
        String inputSnippet,
        String label,
        double confidence,
        double biasScore,
        String emotion,
        String language,
        List<String> highlightedText,
        List<String> suspiciousPhrases,
        String modelVersion,
        String classifierMode,
        Instant createdAt
) {}
