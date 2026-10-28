package com.truthguard.backend.dto;

import java.util.Map;

public record AdminStatsResponse(
        long totalScans,
        long fakeCount,
        long realCount,
        long propagandaCount,
        long users,
        long feedbackCount,
        long scansToday,
        String activeModelVersion,
        Map<String, Long> trendsByDate
) {}
