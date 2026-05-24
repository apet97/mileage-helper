package com.cake.clockify.client;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ClockifyRawClient {
    private static final Set<String> ALLOWED_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private final ClockifyTransport transport;

    public ClockifyRawClient(ClockifyTransport transport) {
        this.transport = transport;
    }

    public ClockifyResponse<String> get(String path, Map<String, ?> query) throws IOException, InterruptedException {
        return request("GET", path, query, null);
    }

    public ClockifyResponse<String> request(String method, String path, Map<String, ?> query, String jsonBody) throws IOException, InterruptedException {
        ClockifyRequest.Builder builder = ClockifyRequest.builder(method, path);
        if (query != null) query.forEach(builder::query);
        if (jsonBody != null) builder.jsonBody(jsonBody);
        return send(builder.build());
    }

    public ClockifyResponse<String> send(ClockifyRequest request) throws IOException, InterruptedException {
        validate(request);
        if (request.binaryResponse()) throw new IllegalArgumentException("Use sendBinary for binary raw requests");
        return transport.send(request);
    }

    public ClockifyBinaryResponse sendBinary(ClockifyRequest request) throws IOException, InterruptedException {
        validate(request);
        return transport.sendBinary(request);
    }

    private static void validate(ClockifyRequest request) {
        String method = request.method().toUpperCase(Locale.ROOT);
        if (!ALLOWED_METHODS.contains(method)) throw new IllegalArgumentException("Unsupported HTTP method: " + request.method());
        if (request.path().contains("://") || request.path().startsWith("//")) {
            throw new IllegalArgumentException("Raw Clockify path must be relative, not an absolute URL");
        }
        if (!request.path().startsWith("/v1/") && !request.path().startsWith("/addon/")) {
            throw new IllegalArgumentException("Raw Clockify path must start with /v1/ or /addon/");
        }
    }
}
