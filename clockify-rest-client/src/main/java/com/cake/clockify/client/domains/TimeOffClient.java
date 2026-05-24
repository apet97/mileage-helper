package com.cake.clockify.client.domains;

import com.cake.clockify.client.ClockifyBaseUrlFamily;
import com.cake.clockify.client.ClockifyRequest;
import com.cake.clockify.client.ClockifyTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

import static com.cake.clockify.client.ClockifyPath.segment;

public final class TimeOffClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public TimeOffClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode getBalancesForPolicy(String workspaceId, String policyId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("policyId", policyId);
        return readJson(ClockifyRequest.builder("GET", balancePath(workspaceId) + "/policy/" + segment("policyId", policyId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND).build());
    }

    public JsonNode updateBalance(String workspaceId, String policyId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("policyId", policyId);
        return sendJson("PATCH", balancePath(workspaceId) + "/policy/" + segment("policyId", policyId), requestBody);
    }

    public JsonNode getBalanceForUser(String workspaceId, String userId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        return readJson(ClockifyRequest.builder("GET", balancePath(workspaceId) + "/user/" + segment("userId", userId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND).build());
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

    public JsonNode changePolicyStatus(String workspaceId, String policyId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("policyId", policyId);
        return sendJson("PATCH", policyPath(workspaceId, policyId), requestBody);
    }

    public JsonNode deletePolicy(String workspaceId, String policyId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("policyId", policyId);
        return readJson(ClockifyRequest.builder("DELETE", policyPath(workspaceId, policyId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND).build());
    }

    public JsonNode createRequest(String workspaceId, String policyId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("policyId", policyId);
        return sendJson("POST", policyPath(workspaceId, policyId) + "/requests", requestBody);
    }

    public JsonNode changeRequestStatus(String workspaceId, String policyId, String requestId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("policyId", policyId);
        requireId("requestId", requestId);
        return sendJson("PATCH", policyPath(workspaceId, policyId) + "/requests/" + segment("requestId", requestId), requestBody);
    }

    public JsonNode deleteRequest(String workspaceId, String policyId, String requestId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("policyId", policyId);
        requireId("requestId", requestId);
        return readJson(ClockifyRequest.builder("DELETE", policyPath(workspaceId, policyId) + "/requests/" + segment("requestId", requestId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND).build());
    }

    public JsonNode createRequestForUser(String workspaceId, String policyId, String userId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("policyId", policyId);
        requireId("userId", userId);
        return sendJson("POST", policyPath(workspaceId, policyId) + "/users/" + segment("userId", userId) + "/requests", requestBody);
    }

    public JsonNode getRequests(String workspaceId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return readJson(ClockifyRequest.builder("GET", requestsPath(workspaceId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND).build());
    }

    public JsonNode filterRequests(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("POST", requestsPath(workspaceId), requestBody);
    }

    public JsonNode filterRequestsForUser(String workspaceId, String userId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        return sendJson("POST", requestsPath(workspaceId) + "/users/" + segment("userId", userId), requestBody);
    }

    public JsonNode getRequest(String workspaceId, String requestId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("requestId", requestId);
        return readJson(ClockifyRequest.builder("GET", requestsPath(workspaceId) + "/" + segment("requestId", requestId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND).build());
    }

    public JsonNode deleteRequestById(String workspaceId, String requestId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("requestId", requestId);
        return readJson(ClockifyRequest.builder("DELETE", requestsPath(workspaceId) + "/" + segment("requestId", requestId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND).build());
    }

    public JsonNode updateRequestStatus(String workspaceId, String requestId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("requestId", requestId);
        return sendJson("PATCH", requestsPath(workspaceId) + "/" + segment("requestId", requestId) + "/status", requestBody);
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

    private static String balancePath(String workspaceId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/time-off/balance";
    }

    private static String policiesPath(String workspaceId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/time-off/policies";
    }

    private static String policyPath(String workspaceId, String policyId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/time-off/policies/" + segment("policyId", policyId);
    }

    private static String requestsPath(String workspaceId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/time-off/requests";
    }

    private static void requireId(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
