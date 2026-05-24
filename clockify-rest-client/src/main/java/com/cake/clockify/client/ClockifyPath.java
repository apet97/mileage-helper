package com.cake.clockify.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Helpers for building Clockify REST paths from caller-provided identifiers.
 */
public final class ClockifyPath {

    private ClockifyPath() {
    }

    public static String segment(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
