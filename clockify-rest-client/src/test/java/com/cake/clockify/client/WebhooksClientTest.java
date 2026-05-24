package com.cake.clockify.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebhooksClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void webhookMethodsUseDocumentedPaths() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("[]", "{}", "{}", "{}", "{}", "{}"));
        ClockifyClient client = new ClockifyClient(ClockifyClient.builder().apiKey("secret").buildConfig(), transport);

        client.webhooks().getAddonWebhooks("w1", "addon1");
        client.webhooks().getWebhooks("w1", new ClockifyPageRequest(2, 25));
        client.webhooks().createWebhook("w1", body());
        client.webhooks().getWebhook("w1", "wh1");
        client.webhooks().updateWebhook("w1", "wh1", body());
        client.webhooks().deleteWebhook("w1", "wh1");

        assertRequest(transport.requests.get(0), "GET", "/v1/workspaces/w1/addons/addon1/webhooks", null);
        assertRequest(transport.requests.get(1), "GET", "/v1/workspaces/w1/webhooks?page=2&page-size=25", null);
        assertRequest(transport.requests.get(2), "POST", "/v1/workspaces/w1/webhooks", "marker");
        assertRequest(transport.requests.get(3), "GET", "/v1/workspaces/w1/webhooks/wh1", null);
        assertRequest(transport.requests.get(4), "PUT", "/v1/workspaces/w1/webhooks/wh1", "marker");
        assertRequest(transport.requests.get(5), "DELETE", "/v1/workspaces/w1/webhooks/wh1", null);
    }

    @Test
    void webhookLogAndTokenMethodsUseDocumentedPaths() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("{}", "[]", "{}", "{}"));
        ClockifyClient client = new ClockifyClient(ClockifyClient.builder().apiKey("secret").buildConfig(), transport);

        client.webhooks().generateNewToken("w1", "wh1", body());
        client.webhooks().getWebhookLogs("w1", "wh1", new ClockifyPageRequest(1, 10));
        client.webhooks().filterWebhookLogs("w1", "wh1", body());
        client.webhooks().updateWebhookToken("w1", "wh1", body());

        assertRequest(transport.requests.get(0), "PATCH", "/v1/workspaces/w1/webhooks/wh1/token", "marker");
        assertRequest(transport.requests.get(1), "GET", "/v1/workspaces/w1/webhooks/wh1/logs?page=1&page-size=10", null);
        assertRequest(transport.requests.get(2), "POST", "/v1/workspaces/w1/webhooks/wh1/logs", "marker");
        assertRequest(transport.requests.get(3), "PATCH", "/v1/workspaces/w1/webhooks/wh1/token", "marker");
    }

    @Test
    void requiredIdsAndBodiesAreValidated() {
        ClockifyClient client = new ClockifyClient(ClockifyClient.builder().apiKey("secret").buildConfig(), new RecordingTransport(List.of()));
        assertThrows(IllegalArgumentException.class, () -> client.webhooks().getWebhooks(" ", new ClockifyPageRequest(1, 10)));
        assertThrows(NullPointerException.class, () -> client.webhooks().getWebhooks("w1", null));
        assertThrows(IllegalArgumentException.class, () -> client.webhooks().getWebhook("w1", " "));
        assertThrows(NullPointerException.class, () -> client.webhooks().createWebhook("w1", null));
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
