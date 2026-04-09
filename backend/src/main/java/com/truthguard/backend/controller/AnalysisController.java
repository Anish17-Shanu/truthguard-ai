package com.truthguard.backend.controller;

import com.truthguard.backend.dto.AnalysisResponse;
import com.truthguard.backend.dto.FeedbackRequest;
import com.truthguard.backend.model.User;
import com.truthguard.backend.service.AnalysisService;
import com.truthguard.backend.service.AuditLogService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping
public class AnalysisController {
    private final AnalysisService analysisService;
    private final AuditLogService auditLogService;

    public AnalysisController(AnalysisService analysisService, AuditLogService auditLogService) {
        this.analysisService = analysisService;
        this.auditLogService = auditLogService;
    }

    @PostMapping(path = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnalysisResponse> analyze(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String url,
            @RequestParam(required = false) MultipartFile file
    ) {
        AnalysisResponse response = analysisService.analyze(user, text, url, file);
        auditLogService.log(user.getId(), "ANALYZE", "/analyze", 200, Map.of("analysisId", response.analysisId()));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    public ResponseEntity<List<AnalysisResponse>> history(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(analysisService.history(user.getId()));
    }

    @GetMapping("/history/{id}")
    public ResponseEntity<AnalysisResponse> historyItem(@AuthenticationPrincipal User user, @PathVariable String id) {
        return ResponseEntity.ok(analysisService.byId(user.getId(), id));
    }

    @PostMapping("/feedback")
    public ResponseEntity<Map<String, String>> feedback(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody FeedbackRequest request
    ) {
        analysisService.submitFeedback(user, request);
        auditLogService.log(user.getId(), "FEEDBACK", "/feedback", 200, Map.of("analysisId", request.analysisId()));
        return ResponseEntity.ok(Map.of("status", "feedback recorded"));
    }
}

