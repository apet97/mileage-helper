package com.cake.clockify.client.domains;

import com.cake.clockify.client.ClockifyBaseUrlFamily;
import com.cake.clockify.client.ClockifyRequest;
import com.cake.clockify.client.ClockifyTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

import static com.cake.clockify.client.ClockifyPath.segment;

public final class CustomFieldsClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public CustomFieldsClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode getCustomFields(String workspaceId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return readJson(ClockifyRequest.builder("GET", workspacePath(workspaceId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build());
    }

    public JsonNode createCustomField(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("POST", workspacePath(workspaceId), requestBody);
    }

    public JsonNode updateCustomField(String workspaceId, String customFieldId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("customFieldId", customFieldId);
        return sendJson("PUT", workspacePath(workspaceId) + "/" + segment("customFieldId", customFieldId), requestBody);
    }

    public void deleteCustomField(String workspaceId, String customFieldId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("customFieldId", customFieldId);
        transport.send(ClockifyRequest.builder("DELETE", workspacePath(workspaceId) + "/" + segment("customFieldId", customFieldId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build());
    }

    public JsonNode getProjectCustomFields(String workspaceId, String projectId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        return readJson(ClockifyRequest.builder("GET", projectPath(workspaceId, projectId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build());
    }

    public JsonNode updateProjectCustomField(String workspaceId, String projectId, String customFieldId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        requireId("customFieldId", customFieldId);
        return sendJson("PATCH", projectPath(workspaceId, projectId) + "/" + segment("customFieldId", customFieldId), requestBody);
    }

    public void deleteProjectCustomField(String workspaceId, String projectId, String customFieldId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        requireId("customFieldId", customFieldId);
        transport.send(ClockifyRequest.builder("DELETE", projectPath(workspaceId, projectId) + "/" + segment("customFieldId", customFieldId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build());
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

    private static String workspacePath(String workspaceId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/custom-fields";
    }

    private static String projectPath(String workspaceId, String projectId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/projects/" + segment("projectId", projectId) + "/custom-fields";
    }

    private static void requireId(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
