package com.truthguard.backend.dto;

import java.util.Map;

public record UserStatsResponse(
        long totalScans,
        long fakeCount,
        long realCount,
        long propagandaCount,
        Map<String, Long> trendsByDate
) {}
