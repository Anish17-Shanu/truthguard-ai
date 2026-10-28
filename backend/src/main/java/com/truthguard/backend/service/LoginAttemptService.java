package com.truthguard.backend.service;

import com.truthguard.backend.exception.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_ENTRIES = 20_000;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public void assertAllowed(String key) {
        Attempt attempt = attempts.get(normalize(key));
        if (attempt == null) {
            return;
        }
        synchronized (attempt) {
            if (attempt.windowStart.plus(WINDOW).isBefore(Instant.now())) {
                attempts.remove(normalize(key));
                return;
            }
            if (attempt.failures >= MAX_ATTEMPTS) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many failed login attempts. Try again later.");
            }
        }
    }

    public void recordFailure(String key) {
        evictIfNeeded();
        attempts.compute(normalize(key), (ignored, current) -> {
            Instant now = Instant.now();
            if (current == null || current.windowStart.plus(WINDOW).isBefore(now)) {
                return new Attempt(now, 1);
            }
            current.failures++;
            return current;
        });
    }

    public void recordSuccess(String key) {
        attempts.remove(normalize(key));
    }

    private void evictIfNeeded() {
        if (attempts.size() < MAX_ENTRIES) {
            return;
        }
        Instant cutoff = Instant.now().minus(WINDOW);
        attempts.entrySet().removeIf(entry -> entry.getValue().windowStart.isBefore(cutoff));
    }

    private String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }

    private static final class Attempt {
        private final Instant windowStart;
        private int failures;

        private Attempt(Instant windowStart, int failures) {
            this.windowStart = windowStart;
            this.failures = failures;
        }
    }
}
