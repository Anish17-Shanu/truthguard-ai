package com.truthguard.backend.service;

import com.truthguard.backend.config.AppProperties;
import com.truthguard.backend.repository.AnalysisRepository;
import com.truthguard.backend.repository.FeedbackRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RetentionJob {
    private final AnalysisRepository analysisRepository;
    private final FeedbackRepository feedbackRepository;
    private final AppProperties properties;

    public RetentionJob(
            AnalysisRepository analysisRepository,
            FeedbackRepository feedbackRepository,
            AppProperties properties
    ) {
        this.analysisRepository = analysisRepository;
        this.feedbackRepository = feedbackRepository;
        this.properties = properties;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purgeExpiredRecords() {
        int days = properties.getRetention().getAnalysesDays();
        if (days <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        long analyses = analysisRepository.deleteByCreatedAtBefore(cutoff);
        long feedback = feedbackRepository.deleteByCreatedAtBefore(cutoff);
        log.info("Retention purge removed analyses={} feedback={} older than {} days", analyses, feedback, days);
    }
}
