package com.truthguard.backend.controller;

import com.truthguard.backend.dto.UserStatsResponse;
import com.truthguard.backend.model.User;
import com.truthguard.backend.service.AnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stats")
public class StatsController {
    private final AnalysisService analysisService;

    public StatsController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserStatsResponse> myStats(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(analysisService.stats(user.getId()));
    }
}
