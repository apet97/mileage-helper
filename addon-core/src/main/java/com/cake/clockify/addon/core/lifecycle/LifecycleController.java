package com.cake.clockify.addon.core.lifecycle;

import com.cake.clockify.addon.core.auth.ClaimsNormalizer;
import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Core lifecycle endpoint router. Dispatches verified lifecycle events to all registered
 * {@link AddonLifecycleHandler} beans. Add-ons register handlers as Spring components — no
 * routing wiring required.
 */
@RestController
@RequestMapping("/lifecycle")
public class LifecycleController {

    private static final Logger log = LoggerFactory.getLogger(LifecycleController.class);

    private final List<AddonLifecycleHandler> handlers;
    private final ObjectMapper objectMapper;

    public LifecycleController(List<AddonLifecycleHandler> handlers, ObjectMapper objectMapper) {
        this.handlers = handlers;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/installed", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> installed(HttpServletRequest request, @RequestBody Map<String, Object> payload) {
        NormalizedClaims claims = ClaimsNormalizer.enrichFromInstalledPayload(
                RequestAttributes.requireClaims(request), payload);
        handlers.forEach(h -> h.onInstalled(claims, payload));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/deleted")
    public ResponseEntity<Void> deleted(HttpServletRequest request,
                                        @RequestBody(required = false) Map<String, Object> payload) {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        for (AddonLifecycleHandler handler : handlers) {
            try {
                handler.onDeleted(claims);
            } catch (Exception e) {
                log.warn("Lifecycle DELETED handler {} failed for workspace {}",
                        handler.getClass().getName(), claims.workspaceId(), e);
            }
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/settings-updated", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> settingsUpdated(HttpServletRequest request,
                                                @RequestBody(required = false) JsonNode payload) {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        List<Map<String, Object>> updates = extractSettingUpdates(payload);
        handlers.forEach(h -> h.onSettingsUpdated(claims, updates));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/status-changed")
    public ResponseEntity<Void> statusChanged(HttpServletRequest request,
                                              @RequestBody(required = false) Map<String, Object> payload) {
        NormalizedClaims claims = RequestAttributes.requireClaims(request);
        String status = payload != null && payload.get("status") instanceof String s ? s : null;
        if (status != null) {
            handlers.forEach(h -> h.onStatusChanged(claims, status));
        }
        return ResponseEntity.ok().build();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractSettingUpdates(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return List.of();
        }
        JsonNode settings = payload.get("settings");
        if (settings == null || !settings.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode item : settings) {
            try {
                Map<String, Object> map = objectMapper.convertValue(item, Map.class);
                result.add(map);
            } catch (Exception ignored) {
            }
        }
        return result;
    }
}
