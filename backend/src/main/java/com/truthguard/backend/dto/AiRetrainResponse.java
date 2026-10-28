package com.truthguard.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiRetrainResponse(
        String status,
        @JsonProperty("new_model_version") String newModelVersion,
        String message
) {}
