package com.truthguard.backend.service;

import com.truthguard.backend.config.AppProperties;
import com.truthguard.backend.dto.AiAnalyzeRequest;
import com.truthguard.backend.dto.AiAnalyzeResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.truthguard.backend.dto.AiFeedbackRequest;
import com.truthguard.backend.dto.AiReadyResponse;
import com.truthguard.backend.dto.AiRetrainResponse;
import com.truthguard.backend.exception.ApiException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Service
public class AiClientService {
    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();

    private final WebClient webClient;
    private final AppProperties properties;

    public AiClientService(AppProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getAiService().getBaseUrl())
                .build();
    }

    @CircuitBreaker(name = "aiService", fallbackMethod = "analyzeFallback")
    public AiAnalyzeResponse analyze(AiAnalyzeRequest request, int timeoutSeconds) {
        try {
            return webClient.post()
                    .uri("/analyze")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        applyInternalToken(headers);
                        applyRequestId(headers);
                    })
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AiAnalyzeResponse.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
        } catch (WebClientResponseException ex) {
            log.warn("AI service rejected analysis: status={} body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ApiException(mapUpstreamStatus(ex), upstreamMessage(ex));
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("AI service call failed: {}", ex.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI service unavailable");
        }
    }

    public AiAnalyzeResponse analyzeFallback(AiAnalyzeRequest request, int timeoutSeconds, Throwable throwable) {
        if (throwable instanceof ApiException apiException) {
            throw apiException;
        }
        log.warn("AI circuit breaker fallback: {}", throwable.getMessage());
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI service unavailable");
    }

    public void submitFeedback(AiFeedbackRequest request) {
        try {
            webClient.post()
                    .uri("/feedback")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        applyInternalToken(headers);
                        applyRequestId(headers);
                    })
                    .bodyValue(request)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(10))
                    .block();
        } catch (Exception ex) {
            log.warn("Could not forward feedback to AI service: {}", ex.getMessage());
        }
    }

    public void deleteUserData(String userId) {
        webClient.delete()
                .uri("/internal/users/{userId}/data", userId)
                .headers(headers -> {
                    applyInternalToken(headers);
                    applyRequestId(headers);
                })
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(10))
                .block();
    }

    public AiRetrainResponse retrain() {
        try {
            return webClient.post()
                    .uri("/retrain")
                    .headers(headers -> {
                        applyInternalToken(headers);
                        applyRequestId(headers);
                    })
                    .retrieve()
                    .bodyToMono(AiRetrainResponse.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
        } catch (WebClientResponseException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Retraining is not available: " + ex.getStatusCode());
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI service unavailable");
        }
    }

    public AiReadyResponse readiness() {
        try {
            return webClient.get()
                    .uri("/ready")
                    .headers(this::applyInternalToken)
                    .retrieve()
                    .bodyToMono(AiReadyResponse.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
        } catch (Exception ex) {
            return null;
        }
    }

    private HttpStatus mapUpstreamStatus(WebClientResponseException ex) {
        int upstream = ex.getStatusCode().value();
        if (upstream == 400 || upstream == 413 || upstream == 429) {
            return HttpStatus.valueOf(upstream);
        }
        if (upstream == 401 || upstream == 403) {
            log.error("AI service rejected the internal token; check AI_INTERNAL_TOKEN on both services");
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (upstream == 422) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.BAD_GATEWAY;
    }

    private String upstreamMessage(WebClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "AI service rejected the request";
        }
        try {
            JsonNode node = ERROR_MAPPER.readTree(body);
            for (String field : new String[] {"message", "detail"}) {
                JsonNode value = node.get(field);
                if (value != null && value.isTextual() && !value.asText().isBlank()) {
                    return value.asText();
                }
            }
        } catch (Exception ignored) {
            // Fall through to the generic message rather than echoing raw upstream output.
        }
        return "AI service rejected the request";
    }

    private void applyInternalToken(org.springframework.http.HttpHeaders headers) {
        String token = properties.getAiService().getInternalToken();
        if (token != null && !token.isBlank()) {
            headers.set("X-Internal-Token", token);
        }
    }

    private void applyRequestId(org.springframework.http.HttpHeaders headers) {
        String requestId = MDC.get("requestId");
        if (requestId != null) {
            headers.set("X-Request-ID", requestId);
        }
    }
}
