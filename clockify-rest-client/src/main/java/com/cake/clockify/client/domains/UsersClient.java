package com.cake.clockify.client.domains;

import com.cake.clockify.client.ClockifyBaseUrlFamily;
import com.cake.clockify.client.ClockifyPageRequest;
import com.cake.clockify.client.ClockifyRequest;
import com.cake.clockify.client.ClockifyTransport;
import com.cake.clockify.client.models.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.cake.clockify.client.ClockifyPath.segment;

public final class UsersClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public UsersClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public User getLoggedUser() throws IOException, InterruptedException {
        return current();
    }

    public User current() throws IOException, InterruptedException {
        ClockifyRequest request = ClockifyRequest.builder("GET", "/v1/user")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        return objectMapper.readValue(transport.send(request).body(), User.class);
    }

    public List<User> getUsersOfWorkspace(String workspaceId, ClockifyPageRequest pageRequest) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        Objects.requireNonNull(pageRequest, "pageRequest");
        ClockifyRequest request = ClockifyRequest.builder("GET", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/users")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .query("page", pageRequest.page())
                .query("page-size", pageRequest.pageSize())
                .build();
        return objectMapper.readValue(transport.send(request).body(), new TypeReference<>() {});
    }

    public User getMemberProfile(String workspaceId, String userId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        ClockifyRequest request = ClockifyRequest.builder("GET", memberProfilePath(workspaceId, userId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        return objectMapper.readValue(transport.send(request).body(), User.class);
    }

    public List<User> getManagersOfUser(String workspaceId, String userId, ClockifyPageRequest pageRequest) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        Objects.requireNonNull(pageRequest, "pageRequest");
        ClockifyRequest request = ClockifyRequest.builder("GET", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/users/" + segment("userId", userId) + "/managers")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .query("page", pageRequest.page())
                .query("page-size", pageRequest.pageSize())
                .build();
        return objectMapper.readValue(transport.send(request).body(), new TypeReference<>() {});
    }

    public List<User> filterUsersOfWorkspace(String workspaceId, Object requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        Objects.requireNonNull(requestBody, "requestBody");
        ClockifyRequest request = ClockifyRequest.builder("POST", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/users/info")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .jsonBody(objectMapper.writeValueAsString(requestBody))
                .build();
        return objectMapper.readValue(transport.send(request).body(), new TypeReference<>() {});
    }

    public User replaceMemberProfile(String workspaceId, String userId, Object requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        return sendJson("PUT", memberProfilePath(workspaceId, userId), requestBody, User.class);
    }

    public User updateMemberProfile(String workspaceId, String userId, Object requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        return sendJson("PATCH", memberProfilePath(workspaceId, userId), requestBody, User.class);
    }

    public User addUserToWorkspace(String workspaceId, Object requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("POST", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/users", requestBody, User.class);
    }

    public User updateUserStatus(String workspaceId, String userId, Object requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        return sendJson("PUT", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/users/" + segment("userId", userId), requestBody, User.class);
    }

    public User updateUserCostRate(String workspaceId, String userId, Object requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        return sendJson("PUT", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/users/" + segment("userId", userId) + "/cost-rate", requestBody, User.class);
    }

    public User updateUserCustomFieldValue(String workspaceId, String userId, String customFieldId, Object requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        requireId("customFieldId", customFieldId);
        return sendJson("PUT", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/users/" + segment("userId", userId) + "/custom-field/" + segment("customFieldId", customFieldId) + "/value", requestBody, User.class);
    }

    public User updateUserHourlyRate(String workspaceId, String userId, Object requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        return sendJson("PUT", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/users/" + segment("userId", userId) + "/hourly-rate", requestBody, User.class);
    }

    public User giveUserManagerRole(String workspaceId, String userId, Object requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        return sendJson("POST", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/users/" + segment("userId", userId) + "/roles", requestBody, User.class);
    }

    public User removeUserManagerRole(String workspaceId, String userId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        ClockifyRequest request = ClockifyRequest.builder("DELETE", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/users/" + segment("userId", userId) + "/roles")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        return objectMapper.readValue(transport.send(request).body(), User.class);
    }

    public Object getUserTimeOffBalances(String workspaceId, String userId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        ClockifyRequest request = ClockifyRequest.builder("GET", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/users/" + segment("userId", userId) + "/time-off/balances")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        return objectMapper.readValue(transport.send(request).body(), Object.class);
    }

    private <T> T sendJson(String method, String path, Object requestBody, Class<T> responseType) throws IOException, InterruptedException {
        Objects.requireNonNull(requestBody, "requestBody");
        ClockifyRequest request = ClockifyRequest.builder(method, path)
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .jsonBody(objectMapper.writeValueAsString(requestBody))
                .build();
        return objectMapper.readValue(transport.send(request).body(), responseType);
    }

    private <T> T sendJson(String method, String path, Object requestBody, TypeReference<T> responseType) throws IOException, InterruptedException {
        Objects.requireNonNull(requestBody, "requestBody");
        ClockifyRequest request = ClockifyRequest.builder(method, path)
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .jsonBody(objectMapper.writeValueAsString(requestBody))
                .build();
        return objectMapper.readValue(transport.send(request).body(), responseType);
    }

    private static String memberProfilePath(String workspaceId, String userId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/member-profile/" + segment("userId", userId);
    }

    public String exchangeUserToken(String userId) throws IOException, InterruptedException {
        requireId("userId", userId);
        ClockifyRequest request = ClockifyRequest.builder("POST", "/addon/user/" + segment("userId", userId) + "/token")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        return transport.send(request).body();
    }

    private static void requireId(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
