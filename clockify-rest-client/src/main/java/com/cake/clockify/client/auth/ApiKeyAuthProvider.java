package com.cake.clockify.client.auth;

import java.net.http.HttpRequest;
import java.util.Objects;

public final class ApiKeyAuthProvider implements ClockifyAuthProvider {
    private final String apiKey;

    public ApiKeyAuthProvider(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey must not be blank");
        }
        this.apiKey = apiKey;
    }

    @Override
    public void apply(HttpRequest.Builder builder) {
        Objects.requireNonNull(builder, "builder").header(headerName(), apiKey);
    }

    @Override
    public String headerName() {
        return "X-Api-Key";
    }

    @Override
    public String headerValue() {
        return apiKey;
    }

    @Override
    public String toString() {
        return "ApiKeyAuthProvider{headerName='X-Api-Key', token='<redacted>'}";
    }
}
