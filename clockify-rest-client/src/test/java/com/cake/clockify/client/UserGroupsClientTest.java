package com.cake.clockify.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserGroupsClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void userGroupMethodsUseDocumentedPathsAndBackendBase() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("[]", "{}", "{}", "{}", "{}", "{}"));
        ClockifyClient client = TestClockifyClient.client(transport);

        assertTrue(client.userGroups().getUserGroups("w1", new ClockifyPageRequest(1, 50)).isArray());
        client.userGroups().createUserGroup("w1", objectMapper.createObjectNode().put("name", "example"));
        client.userGroups().updateUserGroup("w1", "g1", objectMapper.createObjectNode().put("name", "updated"));
        client.userGroups().deleteUserGroup("w1", "g1");
        client.userGroups().addUser("w1", "g1", objectMapper.createObjectNode().put("userId", "u1"));
        client.userGroups().deleteUser("w1", "g1", "u1");

        assertRequest(transport.requests.get(0), "GET", "/v1/workspaces/w1/user-groups?page=1&page-size=50", null);
        assertRequest(transport.requests.get(1), "POST", "/v1/workspaces/w1/user-groups", "name");
        assertRequest(transport.requests.get(2), "PUT", "/v1/workspaces/w1/user-groups/g1", "name");
        assertRequest(transport.requests.get(3), "DELETE", "/v1/workspaces/w1/user-groups/g1", null);
        assertRequest(transport.requests.get(4), "POST", "/v1/workspaces/w1/user-groups/g1/users", "userId");
        assertRequest(transport.requests.get(5), "DELETE", "/v1/workspaces/w1/user-groups/g1/users/u1", null);
    }

    @Test
    void requiredIdsAndBodiesAreValidated() {
        ClockifyClient client = TestClockifyClient.client(new RecordingTransport(List.of()));
        assertThrows(IllegalArgumentException.class, () -> client.userGroups().getUserGroups(" ", new ClockifyPageRequest(1, 10)));
        assertThrows(NullPointerException.class, () -> client.userGroups().getUserGroups("w1", null));
        assertThrows(NullPointerException.class, () -> client.userGroups().createUserGroup("w1", null));
        assertThrows(IllegalArgumentException.class, () -> client.userGroups().updateUserGroup("w1", " ", objectMapper.createObjectNode()));
        assertThrows(IllegalArgumentException.class, () -> client.userGroups().deleteUser("w1", "g1", " "));
    }

    private static void assertRequest(ClockifyRequest request, String method, String pathWithQuery, String bodyContains) {
        assertEquals(method, request.method());
        assertEquals(pathWithQuery, request.pathWithQuery());
        assertEquals(ClockifyBaseUrlFamily.BACKEND, request.baseUrlFamily());
        if (bodyContains == null) assertNull(request.jsonBody());
        else assertTrue(request.jsonBody().contains(bodyContains));
    }

    static final class RecordingTransport implements ClockifyTransport {
        final List<String> bodies;
        final List<ClockifyRequest> requests = new ArrayList<>();
        int index;
        RecordingTransport(List<String> bodies) { this.bodies = bodies; }
        @Override public ClockifyResponse<String> send(ClockifyRequest request) { requests.add(request); return new ClockifyResponse<>(200, Map.of(), bodies.get(index++)); }
        @Override public ClockifyBinaryResponse sendBinary(ClockifyRequest request) { throw new UnsupportedOperationException("not used"); }
    }
}
