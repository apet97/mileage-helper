package com.cake.clockify.addon.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Common add-on properties. Add-ons bind these via {@code addon.*} or override with their own
 * {@code @ConfigurationProperties} and supply the required beans.
 */
@ConfigurationProperties(prefix = "addon")
public record ClockifyAddonProperties(
        String key,
        String name,
        String baseUrl,
        String description,
        @DefaultValue("true") boolean enableHsts,
        @DefaultValue("") String corsAllowedOriginPatterns,
        @DefaultValue("") String extraFrameAncestors,
        Crypto crypto) {

    public List<String> corsAllowedOriginPatternList() {
        if (corsAllowedOriginPatterns == null || corsAllowedOriginPatterns.isBlank()) {
            return List.of("https://*.clockify.me", "https://app.clockify.me", "https://developer.clockify.me");
        }
        return splitCsv(corsAllowedOriginPatterns);
    }

    public List<String> extraFrameAncestorList() {
        if (extraFrameAncestors == null || extraFrameAncestors.isBlank()) {
            return List.of();
        }
        return splitCsv(extraFrameAncestors);
    }

    private static List<String> splitCsv(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    public record Crypto(
            Map<String, String> keys,
            String activeKeyId) {
    }
}
