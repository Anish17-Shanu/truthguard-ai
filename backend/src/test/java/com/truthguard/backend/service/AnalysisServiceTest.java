package com.truthguard.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.truthguard.backend.config.AppProperties;
import com.truthguard.backend.dto.AiFeedbackRequest;
import com.truthguard.backend.dto.FeedbackRequest;
import com.truthguard.backend.model.AnalysisRecord;
import com.truthguard.backend.model.FeedbackRecord;
import com.truthguard.backend.model.User;
import com.truthguard.backend.repository.AnalysisRepository;
import com.truthguard.backend.repository.FeedbackRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AnalysisServiceTest {
    private AnalysisRepository analysisRepository;
    private FeedbackRepository feedbackRepository;
    private AiClientService aiClientService;
    private AnalysisService analysisService;

    @BeforeEach
    void setup() {
        analysisRepository = Mockito.mock(AnalysisRepository.class);
        feedbackRepository = Mockito.mock(FeedbackRepository.class);
        aiClientService = Mockito.mock(AiClientService.class);
        analysisService = new AnalysisService(
                analysisRepository,
                feedbackRepository,
                aiClientService,
                Mockito.mock(AnalysisQuotaService.class),
                Mockito.mock(AppProperties.class));
    }

    @Test
    void forwardsOwnerIdAndContentHashWithFeedback() {
        AnalysisRecord analysis = AnalysisRecord.builder()
                .id("analysis-1")
                .userId("user-1")
                .label("fake")
                .modelVersion("v1")
                .contentHash("hash-1")
                .createdAt(Instant.now())
                .build();
        when(analysisRepository.findByIdAndUserId("analysis-1", "user-1")).thenReturn(Optional.of(analysis));
        when(feedbackRepository.findByAnalysisIdAndUserId("analysis-1", "user-1")).thenReturn(Optional.empty());
        when(feedbackRepository.save(any(FeedbackRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = User.builder().id("user-1").build();
        analysisService.submitFeedback(user, new FeedbackRequest("analysis-1", false, "real", "context"));

        ArgumentCaptor<AiFeedbackRequest> captor = ArgumentCaptor.forClass(AiFeedbackRequest.class);
        verify(aiClientService).submitFeedback(captor.capture());
        assertEquals("user-1", captor.getValue().userId());
        assertEquals("analysis-1", captor.getValue().analysisId());
        assertEquals("hash-1", captor.getValue().contentHash());
    }
}
