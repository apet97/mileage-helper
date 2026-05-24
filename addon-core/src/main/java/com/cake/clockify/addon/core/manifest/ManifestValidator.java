package com.cake.clockify.addon.core.manifest;

import com.cake.clockify.addonsdk.clockify.model.ClockifyManifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates the {@link ClockifyManifest} bean at startup.
 * Fails fast if mandatory fields are missing to prevent runtime errors during Clockify manifest discovery.
 */
@Component
public class ManifestValidator implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ManifestValidator.class);

    private final ClockifyManifest manifest;
    private boolean running = false;

    public ManifestValidator(ClockifyManifest manifest) {
        this.manifest = manifest;
    }

    @Override
    public void start() {
        log.info("Validating Clockify Manifest...");
        List<String> errors = new ArrayList<>();

        if (!StringUtils.hasText(manifest.getKey())) errors.add("key");
        if (StringUtils.hasText(manifest.getKey()) && (manifest.getKey().length() < 2 || manifest.getKey().length() > 50)) {
            errors.add("key length must be 2-50");
        }
        // schemaVersion is usually set by the builder, but let's check it.
        if (!StringUtils.hasText(manifest.getSchemaVersion())) errors.add("schemaVersion");
        String name = readString("getName");
        if (!StringUtils.hasText(name)) {
            errors.add("name");
        } else if (name.length() > 50) {
            errors.add("name length must be 1-50");
        }
        String baseUrl = readString("getBaseUrl");
        if (!StringUtils.hasText(baseUrl)) {
            errors.add("baseUrl");
        } else {
            validateBaseUrl(baseUrl, errors);
        }
        String minimalSubscriptionPlan = readString("getMinimalSubscriptionPlan");
        if (!StringUtils.hasText(minimalSubscriptionPlan)) {
            errors.add("minimalSubscriptionPlan");
        }
        validateUniqueStrings(readList("getScopes"), "scopes", errors);
        validateUniqueResourceKeys(manifest.getLifecycle(), "lifecycle", errors);
        validateUniqueResourceKeys(manifest.getWebhooks(), "webhooks", errors);
        validateUniqueResourceKeys(manifest.getComponents(), "components", errors);

        if (!errors.isEmpty()) {
            String msg = "Invalid ClockifyManifest: Missing mandatory fields: " + String.join(", ", errors);
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        log.info("Clockify Manifest valid: key={}, version={}, components={}",
                manifest.getKey(), manifest.getSchemaVersion(), safeSize(manifest.getComponents()));
        this.running = true;
    }

    @Override
    public void stop() {
        this.running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE; // Run late in the startup
    }

    private String readString(String methodName) {
        try {
            Object value = manifest.getClass().getMethod(methodName).invoke(manifest);
            return value instanceof String s ? s : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> readList(String methodName) {
        try {
            Object value = manifest.getClass().getMethod(methodName).invoke(manifest);
            if (value instanceof List<?> list) {
                return (List<Object>) list;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return List.of();
    }

    private static void validateBaseUrl(String raw, List<String> errors) {
        URI uri;
        try {
            uri = URI.create(raw);
        } catch (IllegalArgumentException e) {
            errors.add("baseUrl must be a valid URI");
            return;
        }
        if (uri.getHost() == null || (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))) {
            errors.add("baseUrl must be http or https with a host");
            return;
        }
        if ("http".equals(uri.getScheme()) && !isLocalHost(uri.getHost())) {
            errors.add("baseUrl must use https outside localhost");
        }
    }

    private static boolean isLocalHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
    }

    private static void validateUniqueStrings(List<Object> values, String label, List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (Object value : values) {
            if (value instanceof String s && !seen.add(s)) {
                errors.add(label + " must be unique");
                return;
            }
        }
    }

    private static void validateUniqueResourceKeys(List<?> values, String label, List<String> errors) {
        if (values == null) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (Object value : values) {
            String key = resourceKey(value);
            if (key != null && !seen.add(key)) {
                errors.add(label + " must be unique");
                return;
            }
        }
    }

    private static String resourceKey(Object value) {
        String type = readGetter(value, "getType");
        String path = readGetter(value, "getPath");
        String id = readGetter(value, "getId");
        return String.join("|",
                type == null ? "" : type,
                path == null ? "" : path,
                id == null ? "" : id);
    }

    private static String readGetter(Object value, String methodName) {
        if (value == null) {
            return null;
        }
        try {
            Object out = value.getClass().getMethod(methodName).invoke(value);
            return out instanceof String s ? s : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static int safeSize(List<?> values) {
        return values == null ? 0 : values.size();
    }
}
