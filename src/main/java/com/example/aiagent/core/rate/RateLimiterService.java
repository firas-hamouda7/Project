package com.example.aiagent.core.rate;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private final MeterRegistry meterRegistry;

    public RateLimiterService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    private static class TokenBucket {
        double tokens;
        long lastRefillMillis;
        final double capacity;
        final double refillPerMillis; // tokens per millisecond

        TokenBucket(double capacity, double refillPerSecond) {
            this.capacity = capacity;
            this.refillPerMillis = refillPerSecond / 1000.0;
            this.tokens = capacity;
            this.lastRefillMillis = System.currentTimeMillis();
        }

        synchronized boolean tryConsume(double amount) {
            refill();
            if (tokens >= amount) {
                tokens -= amount;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long delta = now - lastRefillMillis;
            if (delta <= 0) return;
            double add = delta * refillPerMillis;
            tokens = Math.min(capacity, tokens + add);
            lastRefillMillis = now;
        }
    }

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    // default: 1 request per 5 seconds (0.2 rps) with burst capacity 3
    private final double defaultRefillPerSecond = 0.2;
    private final double defaultCapacity = 3.0;

    public boolean allowRequest(String key) {
        if (key == null) key = "ANONYMOUS";
        TokenBucket b = buckets.computeIfAbsent(key, k -> new TokenBucket(defaultCapacity, defaultRefillPerSecond));
        boolean allowed = b.tryConsume(1.0);
        meterRegistry.counter("ghost_employer_rate_limiter_requests_total", "allowed", Boolean.toString(allowed)).increment();
        if (!allowed) {
            meterRegistry.counter("ghost_employer_rate_limiter_rejected_total").increment();
        }
        return allowed;
    }

}
