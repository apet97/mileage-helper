package com.cake.clockify.client;

import java.util.List;
import java.util.Map;

public class ClockifyApiException extends RuntimeException {
    private final String method;
    private final String path;
    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final String sanitizedBody;

    public ClockifyApiException(String message, int statusCode, Map<String, List<String>> headers, String sanitizedBody) {
        this(message, null, null, statusCode, headers, sanitizedBody);
    }

    public ClockifyApiException(String message, String method, String path, int statusCode, Map<String, List<String>> headers, String sanitizedBody) {
        super(message);
        this.method = method;
        this.path = path;
        this.statusCode = statusCode;
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
        this.sanitizedBody = sanitizedBody;
    }

    public String method() { return method; }
    public String path() { return path; }
    public int statusCode() { return statusCode; }
    public Map<String, List<String>> headers() { return headers; }
    public String sanitizedBody() { return sanitizedBody; }
    public String retryAfter() { return firstHeader("Retry-After"); }
    public String requestId() { return firstHeader("X-Request-Id"); }

    public static ClockifyApiException forStatus(int status, Map<String, List<String>> headers, String body) {
        return forStatus(null, null, status, headers, body);
    }

    public static ClockifyApiException forStatus(String method, String path, int status, Map<String, List<String>> headers, String body) {
        String sanitized = sanitize(body);
        String msg = "Clockify API request failed with status " + status + (method == null ? "" : " for " + method + " " + path);
        if (status == 400 || status == 422) return new ClockifyValidationException(msg, method, path, status, headers, sanitized);
        if (status == 401 || status == 403) return new ClockifyAuthException(msg, method, path, status, headers, sanitized);
        if (status == 404) return new ClockifyNotFoundException(msg, method, path, status, headers, sanitized);
        if (status == 429) return new ClockifyRateLimitException(msg, method, path, status, headers, sanitized);
        if (status >= 500) return new ClockifyServerException(msg, method, path, status, headers, sanitized);
        return new ClockifyApiException(msg, method, path, status, headers, sanitized);
    }

    private String firstHeader(String name) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }

    private static String sanitize(String body) {
        if (body == null) return null;
        String truncated = body.length() > 2000 ? body.substring(0, 2000) + "...<truncated>" : body;
        return truncated
                .replaceAll("(?i)(\"(?:x-api-key|x-addon-token|authorization|authToken|addonToken|api[-_]?key|token|password|secret)\"\\s*:\\s*\")[^\"]*(\")", "$1<redacted>$2")
                .replaceAll("(?i)(authorization\\s*:\\s*bearer\\s+)[^,}\\s\\n]+", "$1<redacted>")
                .replaceAll("(?i)(bearer\\s+)[^,}\\s\\n]+", "$1<redacted>")
                .replaceAll("(?i)((?:x-api-key|x-addon-token|authorization|authToken|addonToken|api[-_]?key|token|password|secret)\\s*[:=]\\s*)[^,}\\s\\n]+", "$1<redacted>");
    }
}
