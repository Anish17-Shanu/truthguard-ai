package com.truthguard.backend.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.truthguard.backend.config.AppProperties;
import com.truthguard.backend.exception.ApiException;
import com.truthguard.backend.repository.AnalysisRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

class AnalysisQuotaServiceTest {

    private AppProperties properties(int perMinute, int perDay) {
        AppProperties properties = new AppProperties();
        properties.getRateLimit().setMaxAnalysesPerMinute(perMinute);
        properties.getRateLimit().setMaxAnalysesPerDay(perDay);
        return properties;
    }

    @Test
    void enforcesPerMinuteLimit() {
        AnalysisRepository repository = Mockito.mock(AnalysisRepository.class);
        when(repository.countByUserIdAndCreatedAtAfter(anyString(), any(Instant.class))).thenReturn(0L);
        AnalysisQuotaService service = new AnalysisQuotaService(repository, properties(2, 100));

        assertDoesNotThrow(() -> service.checkAndConsume("u1"));
        assertDoesNotThrow(() -> service.checkAndConsume("u1"));
        ApiException exception = assertThrows(ApiException.class, () -> service.checkAndConsume("u1"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
    }

    @Test
    void enforcesDailyLimit() {
        AnalysisRepository repository = Mockito.mock(AnalysisRepository.class);
        when(repository.countByUserIdAndCreatedAtAfter(anyString(), any(Instant.class))).thenReturn(5L);
        AnalysisQuotaService service = new AnalysisQuotaService(repository, properties(10, 5));

        assertThrows(ApiException.class, () -> service.checkAndConsume("u1"));
    }

    @Test
    void limitsArePerUser() {
        AnalysisRepository repository = Mockito.mock(AnalysisRepository.class);
        when(repository.countByUserIdAndCreatedAtAfter(anyString(), any(Instant.class))).thenReturn(0L);
        AnalysisQuotaService service = new AnalysisQuotaService(repository, properties(1, 100));

        assertDoesNotThrow(() -> service.checkAndConsume("u1"));
        assertDoesNotThrow(() -> service.checkAndConsume("u2"));
    }
}
