package com.truthguard.backend.config;

import io.jsonwebtoken.io.Decoders;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConfigValidator implements InitializingBean {
    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "admin", "admin123", "password", "password123", "admin@12345", "changeme", "change-me");

    private final AppProperties properties;
    private final Environment environment;

    public ConfigValidator(AppProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        boolean prod = environment.acceptsProfiles(Profiles.of("prod"));
        validateJwt(prod);
        validateCors(prod);
        validateInternalToken(prod);
        validateBootstrapAdmin(prod);
    }

    private void validateJwt(boolean prod) {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            if (prod) {
                throw new IllegalStateException("JWT_SECRET must be set in production");
            }
            log.warn("JWT_SECRET is not set; using an ephemeral development key. Tokens will not survive a restart.");
            return;
        }
        try {
            byte[] decoded = Decoders.BASE64.decode(secret);
            if (decoded.length < 32) {
                throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes");
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("JWT_SECRET must be valid Base64", ex);
        }
        if (properties.getJwt().getExpirationSeconds() < 300) {
            throw new IllegalStateException("JWT_EXPIRATION_SECONDS must be at least 300");
        }
    }

    private void validateCors(boolean prod) {
        List<String> origins = Arrays.stream(properties.getCors().getAllowedOrigins().split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        if (origins.isEmpty()) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS must contain at least one origin");
        }
        if (origins.contains("*")) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS cannot be a wildcard when credentials are allowed");
        }
        if (prod && origins.stream().anyMatch(origin -> !origin.startsWith("https://"))) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS must use https in production");
        }
    }

    private void validateInternalToken(boolean prod) {
        String token = properties.getAiService().getInternalToken();
        if ((token == null || token.isBlank()) && prod) {
            throw new IllegalStateException("AI_INTERNAL_TOKEN must be set in production");
        }
        if (token != null && !token.isBlank() && token.length() < 24) {
            throw new IllegalStateException("AI_INTERNAL_TOKEN must be at least 24 characters");
        }
    }

    private void validateBootstrapAdmin(boolean prod) {
        String email = properties.getBootstrap().getAdminEmail();
        String password = properties.getBootstrap().getAdminPassword();
        if (password != null && !password.isBlank()) {
            if (WEAK_PASSWORDS.contains(password.toLowerCase())) {
                throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD is a known weak value");
            }
            if (password.length() < 12) {
                throw new IllegalStateException("BOOTSTRAP_ADMIN_PASSWORD must be at least 12 characters");
            }
        }
        if (prod && (email == null || email.isBlank())) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_EMAIL must be set in production");
        }
    }
}
