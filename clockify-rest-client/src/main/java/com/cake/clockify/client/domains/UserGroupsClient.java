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

public final class UserGroupsClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public UserGroupsClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode getUserGroups(String workspaceId, ClockifyPageRequest pageRequest) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        Objects.requireNonNull(pageRequest, "pageRequest");
        ClockifyRequest request = ClockifyRequest.builder("GET", path(workspaceId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .query("page", pageRequest.page())
                .query("page-size", pageRequest.pageSize())
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    public JsonNode createUserGroup(String workspaceId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("POST", path(workspaceId), requestBody);
    }

    public JsonNode updateUserGroup(String workspaceId, String userGroupId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userGroupId", userGroupId);
        return sendJson("PUT", path(workspaceId) + "/" + segment("userGroupId", userGroupId), requestBody);
    }

    public void deleteUserGroup(String workspaceId, String userGroupId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userGroupId", userGroupId);
        ClockifyRequest request = ClockifyRequest.builder("DELETE", path(workspaceId) + "/" + segment("userGroupId", userGroupId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        transport.send(request);
    }

    public JsonNode addUser(String workspaceId, String userGroupId, JsonNode requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userGroupId", userGroupId);
        return sendJson("POST", path(workspaceId) + "/" + segment("userGroupId", userGroupId) + "/users", requestBody);
    }

    public void deleteUser(String workspaceId, String userGroupId, String userId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userGroupId", userGroupId);
        requireId("userId", userId);
        ClockifyRequest request = ClockifyRequest.builder("DELETE", path(workspaceId) + "/" + segment("userGroupId", userGroupId) + "/users/" + segment("userId", userId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        transport.send(request);
    }

    public JsonNode getUserGroup(String workspaceId, String userGroupId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userGroupId", userGroupId);
        ClockifyRequest request = ClockifyRequest.builder("GET", path(workspaceId) + "/" + segment("userGroupId", userGroupId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    public JsonNode getUserGroupMembers(String workspaceId, String userGroupId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userGroupId", userGroupId);
        ClockifyRequest request = ClockifyRequest.builder("GET", path(workspaceId) + "/" + segment("userGroupId", userGroupId) + "/users")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    private JsonNode sendJson(String method, String path, JsonNode requestBody) throws IOException, InterruptedException {
        Objects.requireNonNull(requestBody, "requestBody");
        ClockifyRequest request = ClockifyRequest.builder(method, path)
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .jsonBody(objectMapper.writeValueAsString(requestBody))
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    private static String path(String workspaceId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/user-groups";
    }

    private static void requireId(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
