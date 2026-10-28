package com.truthguard.backend.controller;

import com.truthguard.backend.dto.AiReadyResponse;
import com.truthguard.backend.service.AiClientService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class HealthController {
    private final MongoTemplate mongoTemplate;
    private final AiClientService aiClientService;

    public HealthController(MongoTemplate mongoTemplate, AiClientService aiClientService) {
        this.mongoTemplate = mongoTemplate;
        this.aiClientService = aiClientService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "timestamp", Instant.now().toString());
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean mongoReady = checkMongo();
        AiReadyResponse ai = aiClientService.readiness();
        boolean aiReady = ai != null && ai.ready();
        body.put("status", mongoReady && aiReady ? "ready" : "degraded");
        body.put("mongo", mongoReady ? "up" : "down");
        body.put("aiService", aiReady ? "up" : "down");
        body.put("classifierMode", ai == null ? "unavailable" : ai.classifierMode());
        body.put("modelVersion", ai == null ? "unavailable" : ai.modelVersion());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(mongoReady && aiReady ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private boolean checkMongo() {
        try {
            mongoTemplate.executeCommand(new Document("ping", 1));
            return true;
        } catch (Exception ex) {
            log.warn("MongoDB readiness check failed: {}", ex.getMessage());
            return false;
        }
    }
}
