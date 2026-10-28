package com.truthguard.backend.repository;

import com.truthguard.backend.model.Session;
import java.time.Instant;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SessionRepository extends MongoRepository<Session, String> {
    boolean existsByIdAndExpiresAtAfter(String id, Instant now);
    void deleteByUserId(String userId);
}
