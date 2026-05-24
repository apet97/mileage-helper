package com.cake.clockify.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TagsClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void tagCrudMethodsUseDocumentedPathsAndBackendBase() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of(
                "[{\"id\":\"t1\"}]",
                "{\"id\":\"t1\"}",
                "{\"id\":\"t1\"}",
                "{\"id\":\"t1\"}",
                "{}"
        ));
        ClockifyClient client = new ClockifyClient(ClockifyClient.builder().apiKey("secret").buildConfig(), transport);

        assertTrue(client.tags().getTags("w1", new ClockifyPageRequest(1, 50)).isArray());
        client.tags().createTag("w1", objectMapper.createObjectNode().put("name", "example"));
        client.tags().getTag("w1", "t1");
        client.tags().updateTag("w1", "t1", objectMapper.createObjectNode().put("name", "example 2"));
        client.tags().deleteTag("w1", "t1");

        assertRequest(transport.requests.get(0), "GET", "/v1/workspaces/w1/tags?page=1&page-size=50", null);
        assertRequest(transport.requests.get(1), "POST", "/v1/workspaces/w1/tags", "name");
        assertRequest(transport.requests.get(2), "GET", "/v1/workspaces/w1/tags/t1", null);
        assertRequest(transport.requests.get(3), "PUT", "/v1/workspaces/w1/tags/t1", "name");
        assertRequest(transport.requests.get(4), "DELETE", "/v1/workspaces/w1/tags/t1", null);
    }

    @Test
    void requiredIdsAndBodiesAreValidated() {
        ClockifyClient client = new ClockifyClient(ClockifyClient.builder().apiKey("secret").buildConfig(), new RecordingTransport(List.of()));
        assertThrows(IllegalArgumentException.class, () -> client.tags().getTags(" ", new ClockifyPageRequest(1, 10)));
        assertThrows(NullPointerException.class, () -> client.tags().getTags("w1", null));
        assertThrows(IllegalArgumentException.class, () -> client.tags().getTag("w1", " "));
        assertThrows(NullPointerException.class, () -> client.tags().createTag("w1", null));
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
