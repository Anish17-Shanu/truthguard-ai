package com.truthguard.backend.service;

import com.truthguard.backend.dto.AdminStatsResponse;
import com.truthguard.backend.dto.AiReadyResponse;
import com.truthguard.backend.dto.AiRetrainResponse;
import com.truthguard.backend.dto.FeedbackView;
import com.truthguard.backend.dto.PagedResponse;
import com.truthguard.backend.model.AnalysisRecord;
import com.truthguard.backend.model.FeedbackRecord;
import com.truthguard.backend.repository.AnalysisRepository;
import com.truthguard.backend.repository.FeedbackRepository;
import com.truthguard.backend.repository.ModelMetadataRepository;
import com.truthguard.backend.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.truthguard.backend.exception.ApiException;

@Service
public class AdminService {
    private static final int MAX_PAGE_SIZE = 50;

    private final AnalysisRepository analysisRepository;
    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final ModelMetadataRepository modelMetadataRepository;
    private final AiClientService aiClientService;

    public AdminService(
            AnalysisRepository analysisRepository,
            FeedbackRepository feedbackRepository,
            UserRepository userRepository,
            ModelMetadataRepository modelMetadataRepository,
            AiClientService aiClientService
    ) {
        this.analysisRepository = analysisRepository;
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
        this.modelMetadataRepository = modelMetadataRepository;
        this.aiClientService = aiClientService;
    }

    public AdminStatsResponse stats() {
        long total = analysisRepository.count();
        long fakeCount = analysisRepository.countByLabelIgnoreCase("fake");
        long realCount = analysisRepository.countByLabelIgnoreCase("real");
        long propagandaCount = analysisRepository.countByLabelIgnoreCase("propaganda");
        long users = userRepository.count();
        long feedbackCount = feedbackRepository.count();

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant todayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        long scansToday = analysisRepository.countByCreatedAtAfter(todayStart);

        Map<String, Long> trends = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            trends.put(today.minusDays(i).toString(), 0L);
        }
        Instant since = today.minusDays(6).atStartOfDay(ZoneOffset.UTC).toInstant();
        analysisRepository.findByCreatedAtAfter(since).forEach(item -> {
            String key = item.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate().toString();
            trends.computeIfPresent(key, (ignored, value) -> value + 1);
        });

        String modelVersion = modelMetadataRepository.findFirstByActiveTrueOrderByTrainedAtDesc()
                .map(model -> model.getVersion())
                .orElseGet(() -> {
                    AiReadyResponse ai = aiClientService.readiness();
                    return ai != null && ai.modelVersion() != null ? ai.modelVersion() : "unregistered";
                });

        return new AdminStatsResponse(
                total, fakeCount, realCount, propagandaCount, users, feedbackCount, scansToday, modelVersion, trends);
    }

    public PagedResponse<FeedbackView> feedback(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<FeedbackRecord> feedbackPage = feedbackRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(safePage, safeSize));

        List<String> analysisIds = feedbackPage.getContent().stream()
                .map(FeedbackRecord::getAnalysisId)
                .distinct()
                .toList();
        Map<String, AnalysisRecord> analyses = analysisRepository.findAllById(analysisIds).stream()
                .collect(Collectors.toMap(AnalysisRecord::getId, Function.identity()));

        List<FeedbackView> items = feedbackPage.getContent().stream()
                .map(item -> {
                    AnalysisRecord analysis = analyses.get(item.getAnalysisId());
                    return new FeedbackView(
                            item.getId(),
                            item.getAnalysisId(),
                            analysis == null ? null : analysis.getInputSnippet(),
                            item.getPredictedLabel(),
                            analysis == null ? 0.0 : analysis.getConfidence(),
                            item.isCorrect(),
                            item.getCorrectedLabel(),
                            item.getComment(),
                            item.getModelVersion(),
                            item.getCreatedAt());
                })
                .toList();

        return PagedResponse.of(items, feedbackPage.getTotalElements(), safePage, safeSize);
    }

    public AiRetrainResponse triggerRetrain() {
        AiRetrainResponse response = aiClientService.retrain();
        if (response == null || response.status() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Retraining service returned an invalid response");
        }
        return response;
    }
}
