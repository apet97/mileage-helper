package com.cake.clockify.client;

import java.time.Duration;
import java.util.Set;

public record ClockifyRetryPolicy(int maxRetries, Duration baseDelay, Duration maxDelay, Set<Integer> retryStatuses) {
    public static ClockifyRetryPolicy none() {
        return new ClockifyRetryPolicy(0, Duration.ZERO, Duration.ZERO, Set.of());
    }

    public static ClockifyRetryPolicy defaults() {
        return new ClockifyRetryPolicy(2, Duration.ofMillis(200), Duration.ofSeconds(2), Set.of(429, 502, 503, 504));
    }

    public ClockifyRetryPolicy {
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be >= 0");
        if (baseDelay == null || baseDelay.isNegative()) throw new IllegalArgumentException("baseDelay must be non-negative");
        if (maxDelay == null || maxDelay.isNegative()) throw new IllegalArgumentException("maxDelay must be non-negative");
        retryStatuses = retryStatuses == null ? Set.of() : Set.copyOf(retryStatuses);
    }

    boolean shouldRetry(int statusCode, int attempt) {
        return attempt <= maxRetries && retryStatuses.contains(statusCode);
    }

    Duration delayForAttempt(int attempt) {
        if (baseDelay.isZero()) return Duration.ZERO;
        long multiplier = 1L << Math.min(Math.max(0, attempt - 1), 10);
        Duration delay = baseDelay.multipliedBy(multiplier);
        return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
    }
}
