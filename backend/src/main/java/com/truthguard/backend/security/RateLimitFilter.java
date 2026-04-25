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
    private final int maxRequestsPerMinute;
    private final Map<String, RateWindow> counters = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties appProperties) {
        this.maxRequestsPerMinute = appProperties.getRateLimit().getMaxRequestsPerMinute();
    }

    @Override
    @SuppressWarnings("null")
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = request.getRemoteAddr();
        RateWindow window = counters.computeIfAbsent(key, ignored -> new RateWindow(Instant.now().getEpochSecond()));
        long currentMinute = Instant.now().getEpochSecond() / 60;

        synchronized (window) {
            if (window.minuteEpoch != currentMinute) {
                window.minuteEpoch = currentMinute;
                window.counter.set(0);
            }
            int current = window.counter.incrementAndGet();
            if (current > maxRequestsPerMinute) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static class RateWindow {
        long minuteEpoch;
        AtomicInteger counter = new AtomicInteger(0);

        RateWindow(long minuteEpoch) {
            this.minuteEpoch = minuteEpoch;
        }
    }
}

