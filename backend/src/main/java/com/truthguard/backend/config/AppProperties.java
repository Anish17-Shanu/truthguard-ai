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
    private Retention retention = new Retention();
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
        private String internalToken;
    }

    @Data
    public static class Cors {
        private String allowedOrigins;

        public List<String> originsList() {
            return java.util.Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
        }
    }

    @Data
    public static class RateLimit {
        private int maxRequestsPerMinute;
        private int maxAnalysesPerMinute;
        private int maxAnalysesPerDay;
    }

    @Data
    public static class Retention {
        private int analysesDays;
        private int auditDays;
    }

    @Data
    public static class Bootstrap {
        private String adminEmail;
        private String adminPassword;
        private String adminName = "TruthGuard Admin";
    }
}
