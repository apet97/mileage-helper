package com.cake.clockify.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HolidaysClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void holidayMethodsUseDocumentedPathsAndBackendBase() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("[]", "[]", "{}", "{}", "{}"));
        ClockifyClient client = TestClockifyClient.client(transport);

        assertTrue(client.holidays().getHolidays("w1").isArray());
        assertTrue(client.holidays().getHolidaysInPeriod("w1", "2026-01-01", "2026-12-31").isArray());
        client.holidays().createHoliday("w1", objectMapper.createObjectNode().put("name", "example"));
        client.holidays().updateHoliday("w1", "h1", objectMapper.createObjectNode().put("name", "updated"));
        client.holidays().deleteHoliday("w1", "h1");

        assertRequest(transport.requests.get(0), "GET", "/v1/workspaces/w1/holidays", null);
        assertRequest(transport.requests.get(1), "GET", "/v1/workspaces/w1/holidays/in-period?start=2026-01-01&end=2026-12-31", null);
        assertRequest(transport.requests.get(2), "POST", "/v1/workspaces/w1/holidays", "name");
        assertRequest(transport.requests.get(3), "PUT", "/v1/workspaces/w1/holidays/h1", "name");
        assertRequest(transport.requests.get(4), "DELETE", "/v1/workspaces/w1/holidays/h1", null);
    }

    @Test
    void requiredIdsAndBodiesAreValidated() {
        ClockifyClient client = TestClockifyClient.client(new RecordingTransport(List.of()));
        assertThrows(IllegalArgumentException.class, () -> client.holidays().getHolidays(" "));
        assertThrows(NullPointerException.class, () -> client.holidays().createHoliday("w1", null));
        assertThrows(IllegalArgumentException.class, () -> client.holidays().updateHoliday("w1", " ", objectMapper.createObjectNode()));
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
