package com.cake.clockify.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UserWorkspaceClientsTest {
    @Test
    void exposesUsersAndWorkspacesClients() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of(
                "{\"id\":\"u1\",\"name\":\"User\"}",
                "[{\"id\":\"w1\",\"name\":\"Workspace\"}]",
                "{\"id\":\"w1\",\"name\":\"Workspace\"}"
        ));
        ClockifyClientConfig config = TestClockifyClient.config();
        ClockifyClient client = new ClockifyClient(config, transport);

        com.cake.clockify.client.models.User user = client.users().getLoggedUser();
        JsonNode workspaces = client.workspaces().getWorkspacesOfUser();
        JsonNode workspace = client.workspaces().getWorkspaceOfUser("w1");

        assertEquals("u1", user.id());
        assertTrue(workspaces.isArray());
        assertEquals("w1", workspace.get("id").asText());
        assertEquals("/v1/user", transport.requests.get(0).path());
        assertEquals("/v1/workspaces", transport.requests.get(1).path());
        assertEquals("/v1/workspaces/w1", transport.requests.get(2).path());
        assertTrue(transport.requests.stream().allMatch(r -> r.baseUrlFamily() == ClockifyBaseUrlFamily.BACKEND));
    }

    @Test
    void getUsersOfWorkspaceUsesDocumentedPathAndPagination() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("[{\"id\":\"u1\"}]"));
        ClockifyClient client = TestClockifyClient.client(transport);

        assertNotNull(client.users().getUsersOfWorkspace("w1", new ClockifyPageRequest(2, 50)));

        ClockifyRequest request = transport.requests.get(0);
        assertEquals("GET", request.method());
        assertEquals("/v1/workspaces/w1/users?page=2&page-size=50", request.pathWithQuery());
        assertEquals(ClockifyBaseUrlFamily.BACKEND, request.baseUrlFamily());
    }

    @Test
    void userProfileManagersAndFilterUseDocumentedPaths() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of(
                "{\"id\":\"u1\"}",
                "[]",
                "[{\"id\":\"u1\"}]"
        ));
        ClockifyClient client = TestClockifyClient.client(transport);

        assertEquals("u1", client.users().getMemberProfile("w1", "u1").id());
        assertNotNull(client.users().getManagersOfUser("w1", "u1", new ClockifyPageRequest(1, 20)));
        assertNotNull(client.users().filterUsersOfWorkspace("w1", com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()));

        assertEquals("/v1/workspaces/w1/member-profile/u1", transport.requests.get(0).pathWithQuery());
        assertEquals("/v1/workspaces/w1/users/u1/managers?page=1&page-size=20", transport.requests.get(1).pathWithQuery());
        assertEquals("POST", transport.requests.get(2).method());
        assertEquals("/v1/workspaces/w1/users/info", transport.requests.get(2).pathWithQuery());
        assertTrue(transport.requests.stream().allMatch(r -> r.baseUrlFamily() == ClockifyBaseUrlFamily.BACKEND));
    }

    @Test
    void exchangeUserTokenCallsDocumentedPathAndReturnsRawString() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("mock-user-token-123"));
        ClockifyClient client = TestClockifyClient.client(transport);

        String userToken = client.users().exchangeUserToken("u1");

        assertEquals("mock-user-token-123", userToken);
        ClockifyRequest request = transport.requests.get(0);
        assertEquals("POST", request.method());
        assertEquals("/addon/user/u1/token", request.pathWithQuery());
        assertEquals(ClockifyBaseUrlFamily.BACKEND, request.baseUrlFamily());
    }

    @Test
    void workspaceIdIsRequired() {
        ClockifyClient client = TestClockifyClient.client(new RecordingTransport(List.of()));
        assertThrows(IllegalArgumentException.class, () -> client.workspaces().getWorkspaceOfUser(" "));
        assertThrows(IllegalArgumentException.class, () -> client.users().getUsersOfWorkspace(" ", new ClockifyPageRequest(1, 10)));
        assertThrows(NullPointerException.class, () -> client.users().getUsersOfWorkspace("w1", null));
        assertThrows(IllegalArgumentException.class, () -> client.users().getMemberProfile("w1", " "));
        assertThrows(IllegalArgumentException.class, () -> client.users().getManagersOfUser("w1", " ", new ClockifyPageRequest(1, 10)));
        assertThrows(NullPointerException.class, () -> client.users().getManagersOfUser("w1", "u1", null));
        assertThrows(NullPointerException.class, () -> client.users().filterUsersOfWorkspace("w1", null));
    }

    @Test
    void defaultTransportJoinsBaseUrlWithoutDroppingApiPrefix() {
        assertEquals("https://backend.example.test/api/v1/user",
                DefaultClockifyTransport.join(java.net.URI.create("https://backend.example.test/api"), "/v1/user").toString());
    }

    static final class RecordingTransport implements ClockifyTransport {
        final List<String> bodies;
        final List<ClockifyRequest> requests = new ArrayList<>();
        int index;

        RecordingTransport(List<String> bodies) {
            this.bodies = bodies;
        }

        @Override
        public ClockifyResponse<String> send(ClockifyRequest request) {
            requests.add(request);
            return new ClockifyResponse<>(200, Map.of(), bodies.get(index++));
        }

        @Override
        public ClockifyBinaryResponse sendBinary(ClockifyRequest request) {
            throw new UnsupportedOperationException("not used");
        }
    }
}
