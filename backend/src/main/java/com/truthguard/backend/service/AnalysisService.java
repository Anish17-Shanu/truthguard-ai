package com.truthguard.backend.service;

import com.truthguard.backend.config.AppProperties;
import com.truthguard.backend.dto.AiAnalyzeRequest;
import com.truthguard.backend.dto.AiAnalyzeResponse;
import com.truthguard.backend.dto.AnalysisResponse;
import com.truthguard.backend.dto.FeedbackRequest;
import com.truthguard.backend.exception.ApiException;
import com.truthguard.backend.model.AnalysisRecord;
import com.truthguard.backend.model.FeedbackRecord;
import com.truthguard.backend.model.User;
import com.truthguard.backend.repository.AnalysisRepository;
import com.truthguard.backend.repository.FeedbackRepository;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AnalysisService {
    private final AnalysisRepository analysisRepository;
    private final FeedbackRepository feedbackRepository;
    private final AiClientService aiClientService;
    private final AppProperties appProperties;

    public AnalysisService(
            AnalysisRepository analysisRepository,
            FeedbackRepository feedbackRepository,
            AiClientService aiClientService,
            AppProperties appProperties
    ) {
        this.analysisRepository = analysisRepository;
        this.feedbackRepository = feedbackRepository;
        this.aiClientService = aiClientService;
        this.appProperties = appProperties;
    }

    public AnalysisResponse analyze(User user, String text, String url, MultipartFile file) {
        if ((text == null || text.isBlank()) && (url == null || url.isBlank()) && (file == null || file.isEmpty())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Provide text, url, or file");
        }

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

        String snippet = text != null ? text : (url != null ? url : fileName);
        if (snippet != null && snippet.length() > 200) {
            snippet = snippet.substring(0, 200);
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
                .createdAt(Instant.now())
                .build());

        return mapResponse(record);
    }

    public List<AnalysisResponse> history(String userId) {
        return analysisRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::mapResponse).toList();
    }

    public AnalysisResponse byId(String userId, String id) {
        AnalysisRecord record = analysisRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Analysis not found"));
        if (!record.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Forbidden");
        }
        return mapResponse(record);
    }

    public void submitFeedback(User user, FeedbackRequest request) {
        feedbackRepository.save(FeedbackRecord.builder()
                .analysisId(request.analysisId())
                .userId(user.getId())
                .correct(request.correct())
                .correctedLabel(request.correctedLabel())
                .comment(request.comment())
                .createdAt(Instant.now())
                .build());
    }

    private AnalysisResponse mapResponse(AnalysisRecord record) {
        return new AnalysisResponse(
                record.getId(),
                record.getLabel(),
                record.getConfidence(),
                record.getBiasScore(),
                record.getEmotion(),
                record.getLanguage(),
                record.getHighlightedText(),
                record.getSuspiciousPhrases(),
                record.getModelVersion(),
                record.getCreatedAt());
    }
}

