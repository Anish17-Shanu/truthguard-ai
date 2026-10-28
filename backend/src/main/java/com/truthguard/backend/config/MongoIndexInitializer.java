package com.truthguard.backend.config;

import com.truthguard.backend.model.AnalysisRecord;
import com.truthguard.backend.model.AuditLog;
import com.truthguard.backend.model.FeedbackRecord;
import com.truthguard.backend.model.ModelMetadata;
import com.truthguard.backend.model.Session;
import com.truthguard.backend.model.User;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MongoIndexInitializer implements ApplicationRunner {
    private final MongoTemplate mongoTemplate;
    private final AppProperties properties;

    public MongoIndexInitializer(MongoTemplate mongoTemplate, AppProperties properties) {
        this.mongoTemplate = mongoTemplate;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureIndexes();
    }

    public void ensureIndexes() {
        mongoTemplate.indexOps(User.class).ensureIndex(new Index().on("email", Sort.Direction.ASC).unique());

        mongoTemplate.indexOps(AnalysisRecord.class).ensureIndex(
                new Index().on("userId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC).named("user_created"));
        mongoTemplate.indexOps(AnalysisRecord.class).ensureIndex(
                new Index().on("label", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC).named("label_created"));
        mongoTemplate.indexOps(AnalysisRecord.class).ensureIndex(
                new Index().on("createdAt", Sort.Direction.DESC).named("created_at"));

        try {
            mongoTemplate.indexOps(FeedbackRecord.class).ensureIndex(
                    new Index().on("analysisId", Sort.Direction.ASC).on("userId", Sort.Direction.ASC)
                            .unique().named("feedback_analysis_user"));
        } catch (Exception ex) {
            log.warn("Could not create unique feedback index (existing duplicates?): {}", ex.getMessage());
        }
        mongoTemplate.indexOps(FeedbackRecord.class).ensureIndex(
                new Index().on("userId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC).named("feedback_user_created"));

        mongoTemplate.indexOps(Session.class).ensureIndex(
                new Index().on("expiresAt", Sort.Direction.ASC).expire(0, TimeUnit.SECONDS).named("session_ttl"));
        mongoTemplate.indexOps(Session.class).ensureIndex(
                new Index().on("userId", Sort.Direction.ASC).named("session_user"));

        mongoTemplate.indexOps(AuditLog.class).ensureIndex(
                new Index().on("userId", Sort.Direction.ASC).on("timestamp", Sort.Direction.DESC).named("log_user_time"));
        if (properties.getRetention().getAuditDays() > 0) {
            mongoTemplate.indexOps(AuditLog.class).ensureIndex(
                    new Index().on("timestamp", Sort.Direction.ASC)
                            .expire(properties.getRetention().getAuditDays(), TimeUnit.DAYS)
                            .named("log_ttl"));
        }

        mongoTemplate.indexOps(ModelMetadata.class).ensureIndex(
                new Index().on("active", Sort.Direction.ASC).on("trainedAt", Sort.Direction.DESC).named("model_active"));
        log.info("MongoDB indexes ensured");
    }
}
