package com.truthguard.backend.service;

import com.truthguard.backend.config.AppProperties;
import com.truthguard.backend.exception.ApiException;
import com.truthguard.backend.repository.AnalysisRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AnalysisQuotaService {
    private static final int MAX_COUNTERS = 10_000;

    private final AnalysisRepository analysisRepository;
    private final int maxPerMinute;
    private final int maxPerDay;
    private final Map<String, MinuteWindow> minuteCounters = new ConcurrentHashMap<>();

    public AnalysisQuotaService(AnalysisRepository analysisRepository, AppProperties properties) {
        this.analysisRepository = analysisRepository;
        this.maxPerMinute = properties.getRateLimit().getMaxAnalysesPerMinute();
        this.maxPerDay = properties.getRateLimit().getMaxAnalysesPerDay();
    }

    public void checkAndConsume(String userId) {
        Instant now = Instant.now();
        long currentMinute = now.getEpochSecond() / 60;
        evictIfNeeded(currentMinute);

        MinuteWindow window = minuteCounters.computeIfAbsent(userId, ignored -> new MinuteWindow(currentMinute));
        synchronized (window) {
            if (window.minute != currentMinute) {
                window.minute = currentMinute;
                window.count = 0;
            }
            if (window.count >= maxPerMinute) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Per-minute analysis limit reached");
            }
            window.count++;
        }

        Instant startOfDay = now.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
        if (analysisRepository.countByUserIdAndCreatedAtAfter(userId, startOfDay) >= maxPerDay) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Daily analysis limit reached");
        }
    }

    private void evictIfNeeded(long currentMinute) {
        if (minuteCounters.size() < MAX_COUNTERS) {
            return;
        }
        minuteCounters.entrySet().removeIf(entry -> entry.getValue().minute < currentMinute - 2);
    }

    private static final class MinuteWindow {
        private long minute;
        private int count;

        private MinuteWindow(long minute) {
            this.minute = minute;
        }
    }
}
