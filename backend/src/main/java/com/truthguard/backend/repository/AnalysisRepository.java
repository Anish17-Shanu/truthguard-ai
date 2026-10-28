package com.truthguard.backend.repository;

import com.truthguard.backend.model.AnalysisRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AnalysisRepository extends MongoRepository<AnalysisRecord, String> {
    Page<AnalysisRecord> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    List<AnalysisRecord> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(String userId, Instant after);
    List<AnalysisRecord> findByCreatedAtAfter(Instant after);
    long countByCreatedAtAfter(Instant after);
    Optional<AnalysisRecord> findByIdAndUserId(String id, String userId);
    long countByUserId(String userId);
    long countByUserIdAndCreatedAtAfter(String userId, Instant after);
    long countByUserIdAndLabelIgnoreCase(String userId, String label);
    long countByLabelIgnoreCase(String label);
    long deleteByUserId(String userId);
    long deleteByCreatedAtBefore(Instant cutoff);
}
