package com.cake.clockify.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClientsClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void clientCrudMethodsUseDocumentedPathsAndBackendBase() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of(
                "[{\"id\":\"c1\"}]",
                "{\"id\":\"c1\"}",
                "{\"id\":\"c1\"}",
                "{\"id\":\"c1\"}",
                "{}",
                "{\"id\":\"c1\",\"archived\":true}"
        ));
        ClockifyClient client = new ClockifyClient(ClockifyClient.builder().apiKey("secret").buildConfig(), transport);

        assertTrue(client.clients().getClients("w1", new ClockifyPageRequest(2, 25)).isArray());
        client.clients().createClient("w1", objectMapper.createObjectNode().put("name", "example"));
        client.clients().getClient("w1", "c1");
        client.clients().updateClient("w1", "c1", objectMapper.createObjectNode().put("name", "example 2"));
        client.clients().deleteClient("w1", "c1");
        client.clients().archiveClient("w1", "c1", objectMapper.createObjectNode().put("archived", true));

        assertRequest(transport.requests.get(0), "GET", "/v1/workspaces/w1/clients?page=2&page-size=25", null);
        assertRequest(transport.requests.get(1), "POST", "/v1/workspaces/w1/clients", "name");
        assertRequest(transport.requests.get(2), "GET", "/v1/workspaces/w1/clients/c1", null);
        assertRequest(transport.requests.get(3), "PUT", "/v1/workspaces/w1/clients/c1", "name");
        assertRequest(transport.requests.get(4), "DELETE", "/v1/workspaces/w1/clients/c1", null);
        assertRequest(transport.requests.get(5), "PUT", "/v1/workspaces/w1/clients/c1", "archived");
    }

    @Test
    void requiredIdsAndBodiesAreValidated() {
        ClockifyClient client = new ClockifyClient(ClockifyClient.builder().apiKey("secret").buildConfig(), new RecordingTransport(List.of()));
        assertThrows(IllegalArgumentException.class, () -> client.clients().getClients(" ", new ClockifyPageRequest(1, 10)));
        assertThrows(NullPointerException.class, () -> client.clients().getClients("w1", null));
        assertThrows(IllegalArgumentException.class, () -> client.clients().getClient("w1", " "));
        assertThrows(NullPointerException.class, () -> client.clients().createClient("w1", null));
    }

    private static void assertRequest(ClockifyRequest request, String method, String pathWithQuery, String bodyContains) {
        assertEquals(method, request.method());
        assertEquals(pathWithQuery, request.pathWithQuery());
        assertEquals(ClockifyBaseUrlFamily.BACKEND, request.baseUrlFamily());
        if (bodyContains == null) {
            assertNull(request.jsonBody());
        } else {
            assertNotNull(request.jsonBody());
            assertTrue(request.jsonBody().contains(bodyContains));
        }
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
