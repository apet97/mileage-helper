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

public final class ApprovalsClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public ApprovalsClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode getApprovalRequests(String workspaceId, ClockifyPageRequest pageRequest) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        Objects.requireNonNull(pageRequest, "pageRequest");
        ClockifyRequest request = ClockifyRequest.builder("GET", approvalsPath(workspaceId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .query("page", pageRequest.page())
                .query("page-size", pageRequest.pageSize())
                .build();
        return readJson(request);
    }

    public JsonNode createApprovalRequest(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("POST", approvalsPath(workspaceId), requestBody);
    }

    public JsonNode resubmitApprovalRequest(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("POST", approvalsPath(workspaceId) + "/resubmit-entries-for-approval", requestBody);
    }

    public JsonNode createApprovalForOther(String workspaceId, String userId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        return sendJson("POST", approvalsPath(workspaceId) + "/users/" + segment("userId", userId), requestBody);
    }

    public JsonNode resubmitApprovalRequestForOther(String workspaceId, String userId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        return sendJson("POST", approvalsPath(workspaceId) + "/users/" + segment("userId", userId) + "/resubmit-entries-for-approval", requestBody);
    }

    public JsonNode updateApprovalStatus(String workspaceId, String approvalRequestId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("approvalRequestId", approvalRequestId);
        return sendJson("PATCH", approvalsPath(workspaceId) + "/" + segment("approvalRequestId", approvalRequestId), requestBody);
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

    private static String approvalsPath(String workspaceId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/approval-requests";
    }

    private static void requireId(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
