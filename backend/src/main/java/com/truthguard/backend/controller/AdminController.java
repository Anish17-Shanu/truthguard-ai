package com.truthguard.backend.controller;

import com.truthguard.backend.dto.AdminStatsResponse;
import com.truthguard.backend.dto.AiRetrainResponse;
import com.truthguard.backend.dto.FeedbackView;
import com.truthguard.backend.dto.PagedResponse;
import com.truthguard.backend.model.User;
import com.truthguard.backend.service.AdminService;
import com.truthguard.backend.service.AuditLogService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final AdminService adminService;
    private final AuditLogService auditLogService;

    public AdminController(AdminService adminService, AuditLogService auditLogService) {
        this.adminService = adminService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> stats(@AuthenticationPrincipal User user) {
        auditLogService.log(user.getId(), "ADMIN_STATS", "/admin/stats", 200, Map.of());
        return ResponseEntity.ok(adminService.stats());
    }

    @GetMapping("/feedback")
    public ResponseEntity<PagedResponse<FeedbackView>> feedback(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminService.feedback(page, size));
    }

    @PostMapping("/retrain")
    public ResponseEntity<AiRetrainResponse> retrain(@AuthenticationPrincipal User user) {
        AiRetrainResponse response = adminService.triggerRetrain();
        auditLogService.log(user.getId(), "ADMIN_RETRAIN", "/admin/retrain", 200, Map.of("status", response.status()));
        return ResponseEntity.ok(response);
    }
}
