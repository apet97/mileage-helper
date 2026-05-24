package com.cake.clockify.client;

import java.util.List;
import java.util.Map;

public final class ClockifyRateLimitException extends ClockifyApiException {
    public ClockifyRateLimitException(String message, int statusCode, Map<String, List<String>> headers, String sanitizedBody) {
        super(message, statusCode, headers, sanitizedBody);
    }

    public ClockifyRateLimitException(String message, String method, String path, int statusCode, Map<String, List<String>> headers, String sanitizedBody) {
        super(message, method, path, statusCode, headers, sanitizedBody);
    }
}
