package com.cake.clockify.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AddonSettingsClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void addonSettingsUsesDocumentedPathsAndBackendBase() throws Exception {
        RecordingTransport transport = new RecordingTransport("{}");
        ClockifyClient client = TestClockifyClient.addonClient(transport);

        client.addonSettings().getSettings("w1");
        ClockifyRequest getRequest = transport.requests.get(0);
        assertEquals("GET", getRequest.method());
        assertEquals("/addon/workspaces/w1/settings", getRequest.pathWithQuery());
        assertEquals(ClockifyBaseUrlFamily.BACKEND, getRequest.baseUrlFamily());

        client.addonSettings().updateSettings("w1", List.of(Map.of("key", "val")));
        ClockifyRequest patchRequest = transport.requests.get(1);
        assertEquals("PATCH", patchRequest.method());
        assertEquals("/addon/workspaces/w1/settings", patchRequest.pathWithQuery());
        assertEquals(ClockifyBaseUrlFamily.BACKEND, patchRequest.baseUrlFamily());
        assertEquals("[{\"key\":\"val\"}]", patchRequest.jsonBody());
    }

    @Test
    void requiredIdsAndBodiesAreValidated() {
        ClockifyClient client = TestClockifyClient.addonClient(new RecordingTransport("[]"));
        assertThrows(IllegalArgumentException.class, () -> client.addonSettings().getSettings(" "));
        assertThrows(IllegalArgumentException.class, () -> client.addonSettings().updateSettings("", objectMapper.createObjectNode()));
        assertThrows(NullPointerException.class, () -> client.addonSettings().updateSettings("w1", (com.fasterxml.jackson.databind.JsonNode) null));
        assertThrows(NullPointerException.class, () -> client.addonSettings().updateSettings("w1", (List<Map<String, Object>>) null));
    }

    static final class RecordingTransport implements ClockifyTransport {
        final String body;
        final List<ClockifyRequest> requests = new ArrayList<>();

        RecordingTransport(String body) {
            this.body = body;
        }

        @Override
        public ClockifyResponse<String> send(ClockifyRequest request) {
            requests.add(request);
            return new ClockifyResponse<>(200, Map.of(), body);
        }

        @Override
        public ClockifyBinaryResponse sendBinary(ClockifyRequest request) {
            throw new UnsupportedOperationException("not used");
        }
    }
}
