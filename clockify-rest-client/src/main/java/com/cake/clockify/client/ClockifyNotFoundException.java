package com.cake.clockify.client;

import java.util.List;
import java.util.Map;

public final class ClockifyNotFoundException extends ClockifyApiException {
    public ClockifyNotFoundException(String message, int statusCode, Map<String, List<String>> headers, String sanitizedBody) {
        super(message, statusCode, headers, sanitizedBody);
    }

    public ClockifyNotFoundException(String message, String method, String path, int statusCode, Map<String, List<String>> headers, String sanitizedBody) {
        super(message, method, path, statusCode, headers, sanitizedBody);
    }
}
