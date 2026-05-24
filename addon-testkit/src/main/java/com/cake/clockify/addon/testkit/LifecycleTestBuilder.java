package com.cake.clockify.addon.testkit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for Clockify Add-on Lifecycle requests (MockMvc).
 */
public class LifecycleTestBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String path = "/lifecycle/installed";
    private String signature = "dummy-lifecycle-signature";
    private final Map<String, Object> payload = new HashMap<>();
    private final List<Map<String, Object>> webhooks = new ArrayList<>();

    public static LifecycleTestBuilder installed(String workspaceId) {
        LifecycleTestBuilder b = new LifecycleTestBuilder();
        b.path = "/lifecycle/installed";
        b.payload.put("workspaceId", workspaceId);
        return b;
    }

    public static LifecycleTestBuilder deleted(String workspaceId) {
        LifecycleTestBuilder b = new LifecycleTestBuilder();
        b.path = "/lifecycle/deleted";
        b.payload.put("workspaceId", workspaceId);
        return b;
    }

    public static LifecycleTestBuilder settingsUpdated(String workspaceId) {
        LifecycleTestBuilder b = new LifecycleTestBuilder();
        b.path = "/lifecycle/settings-updated";
        b.payload.put("workspaceId", workspaceId);
        return b;
    }

    @SuppressWarnings("unchecked")
    public LifecycleTestBuilder setting(String id, Object value) {
        List<Map<String, Object>> list = (List<Map<String, Object>>) payload.computeIfAbsent("settings", k -> new ArrayList<>());
        Map<String, Object> item = new HashMap<>();
        item.put("id", id);
        item.put("value", value);
        list.add(item);
        return this;
    }

    public LifecycleTestBuilder addonId(String addonId) {
        this.payload.put("addonId", addonId);
        return this;
    }

    public LifecycleTestBuilder authToken(String authToken) {
        this.payload.put("authToken", authToken);
        return this;
    }

    public LifecycleTestBuilder webhook(String path, String event, String authToken) {
        Map<String, Object> wh = new HashMap<>();
        wh.put("path", path);
        wh.put("event", event);
        wh.put("authToken", authToken);
        webhooks.add(wh);
        return this;
    }

    public LifecycleTestBuilder signature(String signature) {
        this.signature = signature;
        return this;
    }

    public MockHttpServletRequestBuilder build() throws Exception {
        if (!webhooks.isEmpty()) {
            payload.put("webhooks", webhooks);
        }

        // Auto-configure mock claims for signature verification
        Map<String, Object> finalClaims = new HashMap<>(payload);
        finalClaims.putIfAbsent("backendUrl", "https://api.clockify.me/api");
        finalClaims.putIfAbsent("reportsUrl", "https://reports.api.clockify.me");
        finalClaims.put("iat", System.currentTimeMillis() / 1000);
        finalClaims.put("exp", (System.currentTimeMillis() / 1000) + 3600);
        MockClockifySignatureParser.setClaims(finalClaims);

        return MockMvcRequestBuilders.post(path)
                .header("X-Addon-Lifecycle-Token", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload));
    }
}
