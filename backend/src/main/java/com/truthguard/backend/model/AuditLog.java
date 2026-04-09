package com.truthguard.backend.model;

import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "logs")
public class AuditLog {
    @Id
    private String id;
    private String userId;
    private String action;
    private String endpoint;
    private int status;
    private Map<String, Object> metadata;
    private Instant timestamp;
}
