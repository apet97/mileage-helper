package com.cake.clockify.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EntityChangesClientTest {
    @Test
    void entityChangeMethodsUseDocumentedPathsAndPagination() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("{}", "{}", "{}"));
        ClockifyClient client = TestClockifyClient.client(transport);

        client.entityChanges().created("w1", 1, 10);
        client.entityChanges().updated("w1", 2, 20);
        client.entityChanges().deleted("w1", 3, 30);

        assertRequest(transport.requests.get(0), "/v1/workspaces/w1/entities/created?page=1&limit=10");
        assertRequest(transport.requests.get(1), "/v1/workspaces/w1/entities/updated?page=2&limit=20");
        assertRequest(transport.requests.get(2), "/v1/workspaces/w1/entities/deleted?page=3&limit=30");
    }

    @Test
    void entityChangeMethodsValidateInputs() {
        ClockifyClient client = TestClockifyClient.client(new RecordingTransport(List.of()));
        assertThrows(IllegalArgumentException.class, () -> client.entityChanges().created(" ", 1, 10));
        assertThrows(IllegalArgumentException.class, () -> client.entityChanges().created("w1", 0, 10));
        assertThrows(IllegalArgumentException.class, () -> client.entityChanges().created("w1", 1, 0));
    }

    private static void assertRequest(ClockifyRequest request, String pathWithQuery) {
        assertEquals("GET", request.method());
        assertEquals(pathWithQuery, request.pathWithQuery());
        assertEquals(ClockifyBaseUrlFamily.BACKEND, request.baseUrlFamily());
        assertNull(request.jsonBody());
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
