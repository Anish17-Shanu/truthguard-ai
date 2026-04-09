package com.truthguard.backend.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Jwt jwt = new Jwt();
    private AiService aiService = new AiService();
    private Cors cors = new Cors();
    private RateLimit rateLimit = new RateLimit();
    private Bootstrap bootstrap = new Bootstrap();

    @Data
    public static class Jwt {
        private String secret;
        private long expirationSeconds;
    }

    @Data
    public static class AiService {
        private String baseUrl;
        private int timeoutSeconds;
    }

    @Data
    public static class Cors {
        private String allowedOrigins;

        public List<String> originsList() {
            return List.of(allowedOrigins.split(","));
        }
    }

    @Data
    public static class RateLimit {
        private int maxRequestsPerMinute;
    }

    @Data
    public static class Bootstrap {
        private String adminEmail;
        private String adminPassword;
        private String adminName = "TruthGuard Admin";
    }
}
