package com.truthguard.backend.dto;

import com.truthguard.backend.model.AnalysisRecord;
import com.truthguard.backend.model.FeedbackRecord;
import java.time.Instant;
import java.util.List;

public record AccountExportResponse(
        String userId,
        String fullName,
        String email,
        String role,
        Instant createdAt,
        List<AnalysisRecord> analyses,
        List<FeedbackRecord> feedback
) {}
