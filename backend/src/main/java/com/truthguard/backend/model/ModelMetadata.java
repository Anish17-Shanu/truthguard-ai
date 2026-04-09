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
@Document(collection = "models")
public class ModelMetadata {
    @Id
    private String id;
    private String version;
    private String notes;
    private Map<String, Double> metrics;
    private boolean active;
    private Instant trainedAt;
}
