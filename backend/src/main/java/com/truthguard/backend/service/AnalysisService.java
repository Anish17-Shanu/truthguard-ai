package com.truthguard.backend.service;

import com.truthguard.backend.config.AppProperties;
import com.truthguard.backend.dto.AiAnalyzeRequest;
import com.truthguard.backend.dto.AiAnalyzeResponse;
import com.truthguard.backend.dto.AiFeedbackRequest;
import com.truthguard.backend.dto.AnalysisResponse;
import com.truthguard.backend.dto.FeedbackRequest;
import com.truthguard.backend.dto.PagedResponse;
import com.truthguard.backend.dto.UserStatsResponse;
import com.truthguard.backend.exception.ApiException;
import com.truthguard.backend.model.AnalysisRecord;
import com.truthguard.backend.model.FeedbackRecord;
import com.truthguard.backend.model.User;
import com.truthguard.backend.repository.AnalysisRepository;
import com.truthguard.backend.repository.FeedbackRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AnalysisService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int SNIPPET_LIMIT = 200;

    private final AnalysisRepository analysisRepository;
    private final FeedbackRepository feedbackRepository;
    private final AiClientService aiClientService;
    private final AnalysisQuotaService quotaService;
    private final AppProperties appProperties;

    public AnalysisService(
            AnalysisRepository analysisRepository,
            FeedbackRepository feedbackRepository,
            AiClientService aiClientService,
            AnalysisQuotaService quotaService,
            AppProperties appProperties
    ) {
        this.analysisRepository = analysisRepository;
        this.feedbackRepository = feedbackRepository;
        this.aiClientService = aiClientService;
        this.quotaService = quotaService;
        this.appProperties = appProperties;
    }

    public AnalysisResponse analyze(User user, String text, String url, MultipartFile file) {
        if ((text == null || text.isBlank()) && (url == null || url.isBlank()) && (file == null || file.isEmpty())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Provide text, url, or file");
        }
        if (text != null && text.length() > 20_000) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Text exceeds 20,000 characters");
        }
        quotaService.checkAndConsume(user.getId());

        String fileBase64 = null;
        String fileName = null;
        try {
            if (file != null && !file.isEmpty()) {
                fileBase64 = Base64.getEncoder().encodeToString(file.getBytes());
                fileName = file.getOriginalFilename();
            }
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid file upload");
        }

        AiAnalyzeRequest aiRequest = new AiAnalyzeRequest(text, url, fileBase64, fileName);
        AiAnalyzeResponse aiResult = aiClientService.analyze(aiRequest, appProperties.getAiService().getTimeoutSeconds());

        String snippet = text != null && !text.isBlank() ? text : (url != null && !url.isBlank() ? url : fileName);
        if (snippet != null && snippet.length() > SNIPPET_LIMIT) {
            snippet = snippet.substring(0, SNIPPET_LIMIT);
        }

        AnalysisRecord record = analysisRepository.save(AnalysisRecord.builder()
                .userId(user.getId())
                .sourceType(aiRequest.sourceType())
                .inputSnippet(snippet)
                .label(aiResult.label())
                .confidence(aiResult.confidence())
                .biasScore(aiResult.bias_score())
                .emotion(aiResult.emotion())
                .language(aiResult.language())
                .highlightedText(aiResult.highlighted_text())
                .suspiciousPhrases(aiResult.suspicious_phrases())
                .modelVersion(aiResult.model_version())
                .classifierMode(aiResult.classifier_mode())
                .contentHash(aiResult.content_hash())
                .createdAt(Instant.now())
                .build());

        return mapResponse(record);
    }

    public PagedResponse<AnalysisResponse> history(String userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<AnalysisRecord> result = analysisRepository.findByUserIdOrderByCreatedAtDesc(
                userId,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<AnalysisResponse> items = result.getContent().stream().map(this::mapResponse).toList();
        return PagedResponse.of(items, result.getTotalElements(), safePage, safeSize);
    }

    public AnalysisResponse byId(String userId, String id) {
        AnalysisRecord record = analysisRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Analysis not found"));
        return mapResponse(record);
    }

    public void deleteAnalysis(String userId, String id) {
        AnalysisRecord record = analysisRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Analysis not found"));
        analysisRepository.delete(record);
    }

    public void submitFeedback(User user, FeedbackRequest request) {
        AnalysisRecord analysis = analysisRepository.findByIdAndUserId(request.analysisId(), user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Analysis not found"));

        FeedbackRecord existing = feedbackRepository
                .findByAnalysisIdAndUserId(request.analysisId(), user.getId())
                .orElse(null);

        FeedbackRecord record = existing == null
                ? FeedbackRecord.builder()
                        .analysisId(request.analysisId())
                        .userId(user.getId())
                        .createdAt(Instant.now())
                        .build()
                : existing;

        record.setPredictedLabel(analysis.getLabel());
        record.setCorrect(request.correct());
        record.setCorrectedLabel(request.correctedLabel());
        record.setComment(request.comment());
        record.setModelVersion(analysis.getModelVersion());
        record.setContentHash(analysis.getContentHash());
        record.setCreatedAt(Instant.now());
        feedbackRepository.save(record);

        aiClientService.submitFeedback(new AiFeedbackRequest(
                request.analysisId(),
                user.getId(),
                request.correct(),
                request.correctedLabel(),
                request.comment(),
                analysis.getModelVersion(),
                analysis.getContentHash()));
    }

    public UserStatsResponse stats(String userId) {
        long total = analysisRepository.countByUserId(userId);
        long fake = analysisRepository.countByUserIdAndLabelIgnoreCase(userId, "fake");
        long real = analysisRepository.countByUserIdAndLabelIgnoreCase(userId, "real");
        long propaganda = analysisRepository.countByUserIdAndLabelIgnoreCase(userId, "propaganda");

        Map<String, Long> trends = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int i = 6; i >= 0; i--) {
            trends.put(today.minusDays(i).toString(), 0L);
        }
        Instant since = today.minusDays(6).atStartOfDay(ZoneOffset.UTC).toInstant();
        analysisRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, since)
                .forEach(item -> {
                    String key = item.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate().toString();
                    trends.computeIfPresent(key, (ignored, value) -> value + 1);
                });

        return new UserStatsResponse(total, fake, real, propaganda, trends);
    }

    private AnalysisResponse mapResponse(AnalysisRecord record) {
        return new AnalysisResponse(
                record.getId(),
                record.getSourceType(),
                record.getInputSnippet(),
                record.getLabel(),
                record.getConfidence(),
                record.getBiasScore(),
                record.getEmotion(),
                record.getLanguage(),
                record.getHighlightedText(),
                record.getSuspiciousPhrases(),
                record.getModelVersion(),
                record.getClassifierMode(),
                record.getCreatedAt());
    }
}
