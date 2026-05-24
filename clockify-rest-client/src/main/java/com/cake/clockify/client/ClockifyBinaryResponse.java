package com.cake.clockify.client;

import java.util.List;
import java.util.Map;

public record ClockifyBinaryResponse(int statusCode, Map<String, List<String>> headers, byte[] bytes) {
    public String contentType() {
        return firstHeader("content-type");
    }

    public String firstHeader(String name) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }
}
