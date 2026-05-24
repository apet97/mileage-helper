package com.cake.clockify.client;

import java.util.List;
import java.util.Map;

public final class ClockifyValidationException extends ClockifyApiException {
    public ClockifyValidationException(String message, int statusCode, Map<String, List<String>> headers, String sanitizedBody) {
        super(message, statusCode, headers, sanitizedBody);
    }

    public ClockifyValidationException(String message, String method, String path, int statusCode, Map<String, List<String>> headers, String sanitizedBody) {
        super(message, method, path, statusCode, headers, sanitizedBody);
    }
}
