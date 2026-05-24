package com.cake.clockify.client.domains;

import com.cake.clockify.client.ClockifyBaseUrlFamily;
import com.cake.clockify.client.ClockifyPageRequest;
import com.cake.clockify.client.ClockifyRequest;
import com.cake.clockify.client.ClockifyTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

import static com.cake.clockify.client.ClockifyPath.segment;

public final class TasksClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public TasksClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode getTasks(String workspaceId, String projectId, ClockifyPageRequest pageRequest) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        Objects.requireNonNull(pageRequest, "pageRequest");
        ClockifyRequest request = ClockifyRequest.builder("GET", path(workspaceId, projectId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .query("page", pageRequest.page())
                .query("page-size", pageRequest.pageSize())
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    public JsonNode createTask(String workspaceId, String projectId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        return sendJson("POST", path(workspaceId, projectId), requestBody);
    }

    public JsonNode getTask(String workspaceId, String projectId, String taskId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        requireId("taskId", taskId);
        ClockifyRequest request = ClockifyRequest.builder("GET", path(workspaceId, projectId) + "/" + segment("taskId", taskId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    public JsonNode updateTask(String workspaceId, String projectId, String taskId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        requireId("taskId", taskId);
        return sendJson("PUT", path(workspaceId, projectId) + "/" + segment("taskId", taskId), requestBody);
    }

    public void deleteTask(String workspaceId, String projectId, String taskId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("projectId", projectId);
        requireId("taskId", taskId);
        ClockifyRequest request = ClockifyRequest.builder("DELETE", path(workspaceId, projectId) + "/" + segment("taskId", taskId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        transport.send(request);
    }

    private JsonNode sendJson(String method, String path, JsonNode requestBody) throws IOException, InterruptedException {
        Objects.requireNonNull(requestBody, "requestBody");
        ClockifyRequest request = ClockifyRequest.builder(method, path)
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .jsonBody(objectMapper.writeValueAsString(requestBody))
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    private static String path(String workspaceId, String projectId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/projects/" + segment("projectId", projectId) + "/tasks";
    }

    private static void requireId(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
