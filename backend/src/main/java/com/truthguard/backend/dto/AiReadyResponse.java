package com.truthguard.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiReadyResponse(
        boolean ready,
        @JsonProperty("classifier_mode") String classifierMode,
        @JsonProperty("model_version") String modelVersion
) {}
