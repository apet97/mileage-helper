package com.cake.clockify.client.domains;

import com.cake.clockify.client.ClockifyBaseUrlFamily;
import com.cake.clockify.client.ClockifyRequest;
import com.cake.clockify.client.ClockifyTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

import static com.cake.clockify.client.ClockifyPath.segment;

public final class ReportsClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public ReportsClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode summary(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        return report("summary", workspaceId, requestBody);
    }

    public JsonNode detailed(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        return report("detailed", workspaceId, requestBody);
    }

    public JsonNode weekly(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        return report("weekly", workspaceId, requestBody);
    }

    public JsonNode attendance(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        return report("attendance", workspaceId, requestBody);
    }

    public JsonNode expenseDetailed(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        return report("expenses/detailed", workspaceId, requestBody);
    }

    private JsonNode report(String report, String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        Objects.requireNonNull(requestBody, "requestBody");
        ClockifyRequest request = ClockifyRequest.builder("POST", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/reports/" + report)
                .baseUrlFamily(ClockifyBaseUrlFamily.REPORTS)
                .jsonBody(objectMapper.writeValueAsString(requestBody))
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    private static void requireId(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
