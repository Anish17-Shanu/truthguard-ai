package com.truthguard.backend.security;

import com.truthguard.backend.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_ENTRIES = 20_000;

    private final int maxRequestsPerMinute;
    private final Map<String, RateWindow> counters = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties appProperties) {
        this.maxRequestsPerMinute = appProperties.getRateLimit().getMaxRequestsPerMinute();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/health") || path.startsWith("/ready") || path.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        long currentMinute = Instant.now().getEpochSecond() / 60;
        evictIfNeeded(currentMinute);

        String key = request.getRemoteAddr();
        RateWindow window = counters.computeIfAbsent(key, ignored -> new RateWindow(currentMinute));

        synchronized (window) {
            if (window.minuteEpoch != currentMinute) {
                window.minuteEpoch = currentMinute;
                window.counter.set(0);
            }
            int current = window.counter.incrementAndGet();
            if (current > maxRequestsPerMinute) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("Retry-After", "60");
                response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"rate_limited\",\"message\":\"Rate limit exceeded\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void evictIfNeeded(long currentMinute) {
        if (counters.size() < MAX_ENTRIES) {
            return;
        }
        counters.entrySet().removeIf(entry -> entry.getValue().minuteEpoch < currentMinute - 1);
    }

    private static class RateWindow {
        long minuteEpoch;
        AtomicInteger counter = new AtomicInteger(0);

        RateWindow(long minuteEpoch) {
            this.minuteEpoch = minuteEpoch;
        }
    }
}
