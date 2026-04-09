package com.truthguard.backend.dto;

import java.util.Map;

public record AdminStatsResponse(
        long totalScans,
        long fakeCount,
        long realCount,
        long propagandaCount,
        long users,
        Map<String, Long> trendsByDate
) {}
