package com.truthguard.backend.model;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "analyses")
@CompoundIndex(name = "user_created", def = "{'userId': 1, 'createdAt': -1}")
@CompoundIndex(name = "label_created", def = "{'label': 1, 'createdAt': -1}")
@CompoundIndex(name = "created_at", def = "{'createdAt': -1}")
public class AnalysisRecord {
    @Id
    private String id;
    private String userId;
    private String sourceType;
    private String inputSnippet;
    private String label;
    private double confidence;
    private double biasScore;
    private String emotion;
    private String language;
    private List<String> highlightedText;
    private List<String> suspiciousPhrases;
    private String modelVersion;
    private String classifierMode;
    private String contentHash;
    private Instant createdAt;
}
