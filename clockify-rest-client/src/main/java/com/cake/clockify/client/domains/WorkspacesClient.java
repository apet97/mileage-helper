package com.cake.clockify.client.domains;

import com.cake.clockify.client.ClockifyBaseUrlFamily;
import com.cake.clockify.client.ClockifyRequest;
import com.cake.clockify.client.ClockifyTransport;
import com.cake.clockify.client.models.Workspace;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.cake.clockify.client.ClockifyPath.segment;

public final class WorkspacesClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public WorkspacesClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode getWorkspacesOfUser() throws IOException, InterruptedException {
        ClockifyRequest request = ClockifyRequest.builder("GET", "/v1/workspaces")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    public List<Workspace> list() throws IOException, InterruptedException {
        ClockifyRequest request = ClockifyRequest.builder("GET", "/v1/workspaces")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        return objectMapper.readValue(transport.send(request).body(), new TypeReference<>() {});
    }

    public JsonNode getWorkspaceOfUser(String workspaceId) throws IOException, InterruptedException {
        if (workspaceId == null || workspaceId.isBlank()) throw new IllegalArgumentException("workspaceId must not be blank");
        ClockifyRequest request = ClockifyRequest.builder("GET", "/v1/workspaces/" + segment("workspaceId", workspaceId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    public Workspace get(String workspaceId) throws IOException, InterruptedException {
        if (workspaceId == null || workspaceId.isBlank()) throw new IllegalArgumentException("workspaceId must not be blank");
        ClockifyRequest request = ClockifyRequest.builder("GET", "/v1/workspaces/" + segment("workspaceId", workspaceId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        return objectMapper.readValue(transport.send(request).body(), Workspace.class);
    }

    public JsonNode addWorkspace(JsonNode requestBody) throws IOException, InterruptedException {
        return sendJson("POST", "/v1/workspaces", requestBody);
    }

    public JsonNode updateWorkspace(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("PUT", "/v1/workspaces/" + segment("workspaceId", workspaceId), requestBody);
    }

    public JsonNode getWorkspaceBalance(String workspaceId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return readJson(ClockifyRequest.builder("GET", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/balance")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND).build());
    }

    public JsonNode updateWorkspaceBalance(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("PATCH", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/balance", requestBody);
    }

    public JsonNode updateWorkspaceCostRate(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("PUT", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/cost-rate", requestBody);
    }

    public JsonNode updateWorkspaceBillableRate(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("PUT", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/hourly-rate", requestBody);
    }

    private JsonNode sendJson(String method, String path, JsonNode requestBody) throws IOException, InterruptedException {
        Objects.requireNonNull(requestBody, "requestBody");
        ClockifyRequest request = ClockifyRequest.builder(method, path)
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .jsonBody(objectMapper.writeValueAsString(requestBody))
                .build();
        return readJson(request);
    }

    private JsonNode readJson(ClockifyRequest request) throws IOException, InterruptedException {
        return objectMapper.readTree(transport.send(request).body());
    }

    private static void requireId(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
