package com.cake.clockify.client.domains;

import com.cake.clockify.client.ClockifyBaseUrlFamily;
import com.cake.clockify.client.ClockifyRequest;
import com.cake.clockify.client.ClockifyTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.cake.clockify.client.ClockifyPath.segment;

public final class AddonSettingsClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public AddonSettingsClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode getSettings(String workspaceId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        ClockifyRequest request = ClockifyRequest.builder("GET", "/addon/workspaces/" + segment("workspaceId", workspaceId) + "/settings")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    public void updateSettings(String workspaceId, JsonNode settingsPayload) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        Objects.requireNonNull(settingsPayload, "settingsPayload");
        ClockifyRequest request = ClockifyRequest.builder("PATCH", "/addon/workspaces/" + segment("workspaceId", workspaceId) + "/settings")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .jsonBody(objectMapper.writeValueAsString(settingsPayload))
                .build();
        transport.send(request);
    }

    public void updateSettings(String workspaceId, List<Map<String, Object>> settingsList) throws IOException, InterruptedException {
        Objects.requireNonNull(settingsList, "settingsList");
        updateSettings(workspaceId, objectMapper.valueToTree(settingsList));
    }

    private static void requireId(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
