package com.truthguard.backend.repository;

import com.truthguard.backend.model.FeedbackRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FeedbackRepository extends MongoRepository<FeedbackRecord, String> {
    Optional<FeedbackRecord> findByAnalysisIdAndUserId(String analysisId, String userId);
    Page<FeedbackRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<FeedbackRecord> findByUserIdOrderByCreatedAtDesc(String userId);
    long deleteByUserId(String userId);
    long deleteByCreatedAtBefore(Instant cutoff);
}
