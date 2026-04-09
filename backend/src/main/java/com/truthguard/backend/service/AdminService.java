package com.truthguard.backend.service;

import com.truthguard.backend.dto.AdminStatsResponse;
import com.truthguard.backend.repository.AnalysisRepository;
import com.truthguard.backend.repository.UserRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AdminService {
    private final AnalysisRepository analysisRepository;
    private final UserRepository userRepository;

    public AdminService(AnalysisRepository analysisRepository, UserRepository userRepository) {
        this.analysisRepository = analysisRepository;
        this.userRepository = userRepository;
    }

    public AdminStatsResponse stats() {
        long total = analysisRepository.count();
        long fakeCount = analysisRepository.countByLabelIgnoreCase("fake");
        long realCount = analysisRepository.countByLabelIgnoreCase("real");
        long propagandaCount = analysisRepository.countByLabelIgnoreCase("propaganda");
        long users = userRepository.count();

        Map<String, Long> trends = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now(ZoneOffset.UTC).minusDays(i);
            trends.put(date.toString(), 0L);
        }
        analysisRepository.findAll().forEach(item -> {
            String key = item.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate().toString();
            trends.computeIfPresent(key, (k, v) -> v + 1);
        });

        return new AdminStatsResponse(total, fakeCount, realCount, propagandaCount, users, trends);
    }
}
