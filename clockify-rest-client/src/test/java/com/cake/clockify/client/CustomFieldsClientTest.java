package com.cake.clockify.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CustomFieldsClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void workspaceCustomFieldMethodsUseDocumentedPaths() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("[]", "{}", "{}", "{}"));
        ClockifyClient client = TestClockifyClient.client(transport);

        client.customFields().getCustomFields("w1");
        client.customFields().createCustomField("w1", body());
        client.customFields().updateCustomField("w1", "cf1", body());
        client.customFields().deleteCustomField("w1", "cf1");

        assertRequest(transport.requests.get(0), "GET", "/v1/workspaces/w1/custom-fields", null);
        assertRequest(transport.requests.get(1), "POST", "/v1/workspaces/w1/custom-fields", "marker");
        assertRequest(transport.requests.get(2), "PUT", "/v1/workspaces/w1/custom-fields/cf1", "marker");
        assertRequest(transport.requests.get(3), "DELETE", "/v1/workspaces/w1/custom-fields/cf1", null);
    }

    @Test
    void projectCustomFieldMethodsUseDocumentedPaths() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("[]", "{}", "{}"));
        ClockifyClient client = TestClockifyClient.client(transport);

        client.customFields().getProjectCustomFields("w1", "p1");
        client.customFields().updateProjectCustomField("w1", "p1", "cf1", body());
        client.customFields().deleteProjectCustomField("w1", "p1", "cf1");

        assertRequest(transport.requests.get(0), "GET", "/v1/workspaces/w1/projects/p1/custom-fields", null);
        assertRequest(transport.requests.get(1), "PATCH", "/v1/workspaces/w1/projects/p1/custom-fields/cf1", "marker");
        assertRequest(transport.requests.get(2), "DELETE", "/v1/workspaces/w1/projects/p1/custom-fields/cf1", null);
    }

    @Test
    void requiredIdsAndBodiesAreValidated() {
        ClockifyClient client = TestClockifyClient.client(new RecordingTransport(List.of()));
        assertThrows(IllegalArgumentException.class, () -> client.customFields().getCustomFields(" "));
        assertThrows(IllegalArgumentException.class, () -> client.customFields().getProjectCustomFields("w1", " "));
        assertThrows(IllegalArgumentException.class, () -> client.customFields().updateCustomField("w1", " ", body()));
        assertThrows(NullPointerException.class, () -> client.customFields().createCustomField("w1", null));
    }

    private com.fasterxml.jackson.databind.JsonNode body() {
        return objectMapper.createObjectNode().put("marker", true);
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
