package com.cake.clockify.addon.core.manifest;

import com.cake.clockify.addonsdk.clockify.model.ClockifyManifest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves {@code GET /manifest} with the Clockify manifest JSON. The manifest bean is built at
 * startup and cached — we serialize it once and return the same bytes on every request.
 */
@RestController
public class ManifestController {

    private final String manifestJson;

    public ManifestController(ClockifyManifest manifest, ObjectMapper objectMapper) {
        JsonNode node = objectMapper.valueToTree(manifest);
        stripEmptyArrays(node);
        try {
            this.manifestJson = objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ClockifyManifest at startup", e);
        }
    }

    @GetMapping(value = "/manifest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> manifest() {
        return ResponseEntity.ok(manifestJson);
    }

    /** Removes null/empty arrays from the manifest JSON to keep the output clean. */
    private static void stripEmptyArrays(JsonNode node) {
        if (node == null || !node.isObject()) return;
        var fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            JsonNode child = entry.getValue();
            if (child.isArray() && child.isEmpty()) {
                fields.remove();
            } else {
                stripEmptyArrays(child);
            }
        }
    }
}
