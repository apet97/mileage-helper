package com.cake.clockify.addon.core.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds exact-delivery webhook dedupe keys without collapsing different events for one entity.
 */
final class WebhookDedupeKey {

    private static final int MAX_COMPONENT_LENGTH = 80;

    /** Context fields that appear in most payloads and are never the entity identifier. */
    private static final Set<String> CONTEXT_ID_FIELDS = Set.of(
            "workspaceId", "userId", "projectId", "categoryId", "taskId"
    );

    private WebhookDedupeKey() {
    }

    static Optional<String> from(String eventType, byte[] body, ObjectMapper objectMapper) {
        body = body == null ? new byte[0] : body;
        String normalizedEvent = normalizeEventType(eventType);
        String payloadHash = payloadHash(body);
        try {
            JsonNode root = objectMapper.readTree(body);
            Optional<String> eventId = text(root, "eventId");
            if (eventId.isPresent()) {
                return Optional.of(normalizedEvent + ":eventId:" + safeComponent(eventId.get()) + ":" + payloadHash);
            }
            Optional<String> id = text(root, "id");
            if (id.isPresent()) {
                return Optional.of(normalizedEvent + ":id:" + safeComponent(id.get()) + ":" + payloadHash);
            }
            // Scan for domain-specific entity ID fields (e.g. expenseId, invoiceId).
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String fieldName = entry.getKey();
                if (fieldName.endsWith("Id") && !CONTEXT_ID_FIELDS.contains(fieldName)) {
                    Optional<String> entityId = text(root, fieldName);
                    if (entityId.isPresent()) {
                        return Optional.of(normalizedEvent + ":" + fieldName + ":" + safeComponent(entityId.get()) + ":" + payloadHash);
                    }
                }
            }
            return Optional.of(normalizedEvent + ":payload:" + payloadHash);
        } catch (Exception ignored) {
            return Optional.of(normalizedEvent + ":payload:" + payloadHash);
        }
    }

    static String payloadHash(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is not available", e);
        }
    }

    private static Optional<String> text(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isTextual() && !node.asText().isBlank()) {
            return Optional.of(node.asText());
        }
        return Optional.empty();
    }

    private static String normalizeEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return "UNKNOWN";
        }
        return eventType.trim().toUpperCase(Locale.ROOT);
    }

    private static String safeComponent(String value) {
        String safe = value.trim().replace(':', '_');
        if (safe.length() <= MAX_COMPONENT_LENGTH) {
            return safe;
        }
        return safe.substring(0, MAX_COMPONENT_LENGTH);
    }
}
