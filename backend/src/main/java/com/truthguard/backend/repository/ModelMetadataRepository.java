package com.truthguard.backend.repository;

import com.truthguard.backend.model.ModelMetadata;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ModelMetadataRepository extends MongoRepository<ModelMetadata, String> {
    Optional<ModelMetadata> findFirstByActiveTrueOrderByTrainedAtDesc();
}
