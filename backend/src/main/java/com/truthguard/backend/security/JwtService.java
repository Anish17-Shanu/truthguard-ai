package com.truthguard.backend.security;

import com.truthguard.backend.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JwtService {
    private final AppProperties appProperties;
    private volatile SecretKey signingKey;

    public JwtService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public IssuedToken generateToken(String userId, String email, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(expiresInSeconds());
        String tokenId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(tokenId)
                .subject(userId)
                .claims(Map.of("email", email, "role", role))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey())
                .compact();
        return new IssuedToken(token, tokenId, expiry);
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long expiresInSeconds() {
        return appProperties.getJwt().getExpirationSeconds();
    }

    private SecretKey signingKey() {
        SecretKey current = signingKey;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (signingKey == null) {
                String secret = appProperties.getJwt().getSecret();
                if (secret == null || secret.isBlank()) {
                    byte[] random = new byte[32];
                    new SecureRandom().nextBytes(random);
                    signingKey = Keys.hmacShaKeyFor(random);
                } else {
                    byte[] keyBytes = Decoders.BASE64.decode(secret);
                    signingKey = Keys.hmacShaKeyFor(keyBytes);
                }
            }
            return signingKey;
        }
    }

    public record IssuedToken(String token, String tokenId, Instant expiresAt) {}
}
