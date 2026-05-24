package com.cake.clockify.client.domains;

import com.cake.clockify.client.ClockifyBaseUrlFamily;
import com.cake.clockify.client.ClockifyRequest;
import com.cake.clockify.client.ClockifyTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

import static com.cake.clockify.client.ClockifyPath.segment;

public final class EntityChangesClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public EntityChangesClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode created(String workspaceId, int page, int limit) throws IOException, InterruptedException {
        return list(workspaceId, "created", page, limit);
    }

    public JsonNode updated(String workspaceId, int page, int limit) throws IOException, InterruptedException {
        return list(workspaceId, "updated", page, limit);
    }

    public JsonNode deleted(String workspaceId, int page, int limit) throws IOException, InterruptedException {
        return list(workspaceId, "deleted", page, limit);
    }

    private JsonNode list(String workspaceId, String kind, int page, int limit) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        if (page < 1) throw new IllegalArgumentException("page must be >= 1");
        if (limit < 1) throw new IllegalArgumentException("limit must be >= 1");
        ClockifyRequest request = ClockifyRequest.builder("GET", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/entities/" + kind)
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .query("page", page)
                .query("limit", limit)
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    private static void requireId(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
