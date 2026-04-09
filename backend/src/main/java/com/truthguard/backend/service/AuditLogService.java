package com.truthguard.backend.service;

import com.truthguard.backend.model.AuditLog;
import com.truthguard.backend.repository.AuditLogRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(String userId, String action, String endpoint, int status, Map<String, Object> metadata) {
        auditLogRepository.save(AuditLog.builder()
                .userId(userId)
                .action(action)
                .endpoint(endpoint)
                .status(status)
                .metadata(metadata)
                .timestamp(Instant.now())
                .build());
    }
}

