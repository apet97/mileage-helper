package com.cake.clockify.client.domains;

import com.cake.clockify.client.ClockifyBaseUrlFamily;
import com.cake.clockify.client.ClockifyRequest;
import com.cake.clockify.client.ClockifyTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

import static com.cake.clockify.client.ClockifyPath.segment;

public final class PoliciesClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public PoliciesClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode getPolicies(String workspaceId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return readJson(ClockifyRequest.builder("GET", policiesPath(workspaceId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND).build());
    }

    public JsonNode createPolicy(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("POST", policiesPath(workspaceId), requestBody);
    }

    public JsonNode getPolicy(String workspaceId, String policyId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("policyId", policyId);
        return readJson(ClockifyRequest.builder("GET", policyPath(workspaceId, policyId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND).build());
    }

    public JsonNode updatePolicy(String workspaceId, String policyId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("policyId", policyId);
        return sendJson("PUT", policyPath(workspaceId, policyId), requestBody);
    }

    public JsonNode deletePolicy(String workspaceId, String policyId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("policyId", policyId);
        return readJson(ClockifyRequest.builder("DELETE", policyPath(workspaceId, policyId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND).build());
    }

    public JsonNode archivePolicy(String workspaceId, String policyId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("policyId", policyId);
        return sendJson("PATCH", policyPath(workspaceId, policyId), requestBody);
    }

    public JsonNode createPolicyRequest(String workspaceId, String policyId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("policyId", policyId);
        return sendJson("POST", policyPath(workspaceId, policyId) + "/requests", requestBody);
    }

    public JsonNode updatePolicyRequest(String workspaceId, String policyId, String requestId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("policyId", policyId);
        requireId("requestId", requestId);
        return sendJson("PATCH", policyPath(workspaceId, policyId) + "/requests/" + segment("requestId", requestId), requestBody);
    }

    public JsonNode deletePolicyRequest(String workspaceId, String policyId, String requestId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("policyId", policyId);
        requireId("requestId", requestId);
        return readJson(ClockifyRequest.builder("DELETE", policyPath(workspaceId, policyId) + "/requests/" + segment("requestId", requestId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND).build());
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

    private static String policiesPath(String workspaceId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/time-off/policies";
    }

    private static String policyPath(String workspaceId, String policyId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/time-off/policies/" + segment("policyId", policyId);
    }

    private static void requireId(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
