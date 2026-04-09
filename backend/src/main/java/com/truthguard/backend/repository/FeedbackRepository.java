package com.truthguard.backend.repository;

import com.truthguard.backend.model.FeedbackRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FeedbackRepository extends MongoRepository<FeedbackRecord, String> {
}
