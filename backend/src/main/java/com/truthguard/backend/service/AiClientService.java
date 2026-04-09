package com.truthguard.backend.service;

import com.truthguard.backend.config.AppProperties;
import com.truthguard.backend.dto.AiAnalyzeRequest;
import com.truthguard.backend.dto.AiAnalyzeResponse;
import com.truthguard.backend.exception.ApiException;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class AiClientService {
    private final WebClient webClient;

    public AiClientService(AppProperties properties) {
        this.webClient = WebClient.builder()
                .baseUrl(properties.getAiService().getBaseUrl())
                .build();
    }

    public AiAnalyzeResponse analyze(AiAnalyzeRequest request, int timeoutSeconds) {
        try {
            return webClient.post()
                    .uri("/analyze")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AiAnalyzeResponse.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
        } catch (WebClientResponseException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI service error: " + ex.getResponseBodyAsString());
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AI service unreachable");
        }
    }
}

