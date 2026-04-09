package com.truthguard.backend.repository;

import com.truthguard.backend.model.AnalysisRecord;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AnalysisRepository extends MongoRepository<AnalysisRecord, String> {
    List<AnalysisRecord> findByUserIdOrderByCreatedAtDesc(String userId);
    long countByLabelIgnoreCase(String label);
}
