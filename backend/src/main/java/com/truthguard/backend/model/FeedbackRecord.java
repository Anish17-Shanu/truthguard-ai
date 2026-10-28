package com.truthguard.backend.model;

import java.time.Instant;
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
@Document(collection = "feedback")
@CompoundIndex(name = "feedback_analysis_user", def = "{'analysisId': 1, 'userId': 1}", unique = true)
@CompoundIndex(name = "feedback_user_created", def = "{'userId': 1, 'createdAt': -1}")
public class FeedbackRecord {
    @Id
    private String id;
    private String analysisId;
    private String userId;
    private String predictedLabel;
    private boolean correct;
    private String correctedLabel;
    private String comment;
    private String modelVersion;
    private String contentHash;
    private Instant createdAt;
}
