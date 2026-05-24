package com.cake.clockify.client.domains;

import com.cake.clockify.client.ClockifyBaseUrlFamily;
import com.cake.clockify.client.ClockifyRequest;
import com.cake.clockify.client.ClockifyTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

import static com.cake.clockify.client.ClockifyPath.segment;

public final class SharedReportsClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public SharedReportsClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode getSharedReport(String sharedReportId) throws IOException, InterruptedException {
        requireId("sharedReportId", sharedReportId);
        return readJson(ClockifyRequest.builder("GET", "/v1/shared-reports/" + segment("sharedReportId", sharedReportId))
                .baseUrlFamily(ClockifyBaseUrlFamily.REPORTS).build());
    }

    public JsonNode getWorkspaceSharedReports(String workspaceId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return readJson(ClockifyRequest.builder("GET", sharedReportsPath(workspaceId))
                .baseUrlFamily(ClockifyBaseUrlFamily.REPORTS).build());
    }

    public JsonNode createSharedReport(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("POST", sharedReportsPath(workspaceId), requestBody);
    }

    public JsonNode updateSharedReport(String workspaceId, String sharedReportId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("sharedReportId", sharedReportId);
        return sendJson("PUT", sharedReportsPath(workspaceId) + "/" + segment("sharedReportId", sharedReportId), requestBody);
    }

    public JsonNode deleteSharedReport(String workspaceId, String sharedReportId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("sharedReportId", sharedReportId);
        return readJson(ClockifyRequest.builder("DELETE", sharedReportsPath(workspaceId) + "/" + segment("sharedReportId", sharedReportId))
                .baseUrlFamily(ClockifyBaseUrlFamily.REPORTS).build());
    }

    private JsonNode sendJson(String method, String path, JsonNode requestBody) throws IOException, InterruptedException {
        Objects.requireNonNull(requestBody, "requestBody");
        ClockifyRequest request = ClockifyRequest.builder(method, path)
                .baseUrlFamily(ClockifyBaseUrlFamily.REPORTS)
                .jsonBody(objectMapper.writeValueAsString(requestBody))
                .build();
        return readJson(request);
    }

    private JsonNode readJson(ClockifyRequest request) throws IOException, InterruptedException {
        return objectMapper.readTree(transport.send(request).body());
    }

    private static String sharedReportsPath(String workspaceId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/shared-reports";
    }

    private static void requireId(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
