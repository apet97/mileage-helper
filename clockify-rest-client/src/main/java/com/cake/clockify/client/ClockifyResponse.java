package com.cake.clockify.client;

import java.util.List;
import java.util.Map;

public record ClockifyResponse<T>(int statusCode, Map<String, List<String>> headers, T body) {
    public String firstHeader(String name) {
        if (headers == null) return null;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }
}
