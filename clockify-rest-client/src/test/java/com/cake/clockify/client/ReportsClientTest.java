package com.cake.clockify.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReportsClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void allReportMethodsUseReportsHost() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("{}", "{}", "{}", "{}", "{}"));
        ClockifyClient client = new ClockifyClient(ClockifyClient.builder().apiKey("secret").buildConfig(), transport);

        client.reports().summary("w1", body());
        client.reports().detailed("w1", body());
        client.reports().weekly("w1", body());
        client.reports().attendance("w1", body());
        client.reports().expenseDetailed("w1", body());

        assertReportRequest(transport.requests.get(0), "/v1/workspaces/w1/reports/summary");
        assertReportRequest(transport.requests.get(1), "/v1/workspaces/w1/reports/detailed");
        assertReportRequest(transport.requests.get(2), "/v1/workspaces/w1/reports/weekly");
        assertReportRequest(transport.requests.get(3), "/v1/workspaces/w1/reports/attendance");
        assertReportRequest(transport.requests.get(4), "/v1/workspaces/w1/reports/expenses/detailed");
    }

    @Test
    void reportMethodsValidateIdsAndBodies() {
        ClockifyClient client = new ClockifyClient(ClockifyClient.builder().apiKey("secret").buildConfig(), new RecordingTransport(List.of()));
        assertThrows(IllegalArgumentException.class, () -> client.reports().attendance(" ", body()));
        assertThrows(NullPointerException.class, () -> client.reports().expenseDetailed("w1", null));
    }

    private com.fasterxml.jackson.databind.JsonNode body() {
        return objectMapper.createObjectNode().put("dateRangeStart", "2026-01-01T00:00:00.000Z");
    }

    private static void assertReportRequest(ClockifyRequest request, String path) {
        assertEquals("POST", request.method());
        assertEquals(path, request.pathWithQuery());
        assertEquals(ClockifyBaseUrlFamily.REPORTS, request.baseUrlFamily());
        assertNotNull(request.jsonBody());
        assertTrue(request.jsonBody().contains("dateRangeStart"));
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
