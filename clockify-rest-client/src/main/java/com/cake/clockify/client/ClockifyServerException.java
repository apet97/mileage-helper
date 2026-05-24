package com.cake.clockify.client;

import java.util.List;
import java.util.Map;

public final class ClockifyServerException extends ClockifyApiException {
    public ClockifyServerException(String message, int statusCode, Map<String, List<String>> headers, String sanitizedBody) {
        super(message, statusCode, headers, sanitizedBody);
    }

    public ClockifyServerException(String message, String method, String path, int statusCode, Map<String, List<String>> headers, String sanitizedBody) {
        super(message, method, path, statusCode, headers, sanitizedBody);
    }
}
