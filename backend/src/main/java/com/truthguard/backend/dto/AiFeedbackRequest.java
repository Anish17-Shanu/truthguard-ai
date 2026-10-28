package com.truthguard.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiFeedbackRequest(
        @JsonProperty("analysis_id") String analysisId,
        @JsonProperty("user_id") String userId,
        boolean correct,
        @JsonProperty("corrected_label") String correctedLabel,
        String comment,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("content_hash") String contentHash
) {}
