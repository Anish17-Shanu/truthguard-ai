package com.truthguard.backend.model;

import java.time.Instant;
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
@Document(collection = "feedback")
public class FeedbackRecord {
    @Id
    private String id;
    private String analysisId;
    private String userId;
    private boolean correct;
    private String correctedLabel;
    private String comment;
    private Instant createdAt;
}
