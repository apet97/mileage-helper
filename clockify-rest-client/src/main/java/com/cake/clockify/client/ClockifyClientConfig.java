package com.cake.clockify.client;

import com.cake.clockify.client.auth.ClockifyAuthProvider;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public record ClockifyClientConfig(
        URI backendBaseUrl,
        URI reportsBaseUrl,
        ClockifyAuthProvider authProvider,
        String workspaceId,
        String userAgent,
        Map<String, String> customHeaders,
        ClockifyRetryPolicy retryPolicy,
        Duration requestTimeout,
        int maxResponseBytes,
        boolean followRedirects,
        boolean allowCrossHostRedirects
) {
    public static final int DEFAULT_MAX_RESPONSE_BYTES = 10 * 1024 * 1024;
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    public ClockifyClientConfig {
        backendBaseUrl = normalizeBaseUrl(Objects.requireNonNull(backendBaseUrl, "backendBaseUrl"));
        reportsBaseUrl = reportsBaseUrl == null ? null : normalizeBaseUrl(reportsBaseUrl);
        Objects.requireNonNull(authProvider, "authProvider");
        workspaceId = workspaceId == null || workspaceId.isBlank() ? null : workspaceId.trim();
        userAgent = userAgent == null || userAgent.isBlank() ? "clockify-rest-client/0.1" : userAgent.trim();
        customHeaders = customHeaders == null ? Map.of() : Map.copyOf(customHeaders);
        retryPolicy = retryPolicy == null ? ClockifyRetryPolicy.none() : retryPolicy;
        Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (requestTimeout.isZero() || requestTimeout.isNegative()) throw new IllegalArgumentException("requestTimeout must be positive");
        if (maxResponseBytes <= 0) throw new IllegalArgumentException("maxResponseBytes must be positive");
    }

    public URI baseUrl(ClockifyBaseUrlFamily family) {
        return switch (family) {
            case BACKEND -> backendBaseUrl;
            case REPORTS -> {
                if (reportsBaseUrl == null) {
                    throw new IllegalStateException("reportsBaseUrl is required for reports requests");
                }
                yield reportsBaseUrl;
            }
        };
    }

    private static URI normalizeBaseUrl(URI uri) {
        String text = uri.toString();
        return URI.create(text.endsWith("/") ? text.substring(0, text.length() - 1) : text);
    }

    @Override
    public String toString() {
        return "ClockifyClientConfig{" +
                "backendBaseUrl=" + backendBaseUrl +
                ", reportsBaseUrl=" + reportsBaseUrl +
                ", authProvider=" + authProvider.getClass().getSimpleName() +
                ", workspaceId=" + (workspaceId == null ? "<none>" : "<configured>") +
                ", userAgent='" + userAgent + '\'' +
                ", customHeaders=" + customHeaders.keySet() +
                ", retryPolicy=" + retryPolicy +
                ", requestTimeout=" + requestTimeout +
                ", maxResponseBytes=" + maxResponseBytes +
                ", followRedirects=" + followRedirects +
                ", allowCrossHostRedirects=" + allowCrossHostRedirects +
                '}';
    }
}
