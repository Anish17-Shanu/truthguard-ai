package com.truthguard.backend.dto;

import java.util.List;

public record AiAnalyzeResponse(
        String label,
        double confidence,
        double bias_score,
        String emotion,
        String language,
        List<String> highlighted_text,
        List<String> suspicious_phrases,
        String model_version,
        String classifier_mode,
        String content_hash
) {}
