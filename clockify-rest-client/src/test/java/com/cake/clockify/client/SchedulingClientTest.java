package com.cake.clockify.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchedulingClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void assignmentMethodsUseDocumentedPaths() throws Exception {
        RecordingTransport t = new RecordingTransport(5);
        ClockifyClient c = new ClockifyClient(ClockifyClient.builder().apiKey("secret").buildConfig(), t);
        c.scheduling().createAssignment("w1", body());
        c.scheduling().getAllAssignments("w1");
        c.scheduling().replaceAssignment("w1", "a1", body());
        c.scheduling().copyAssignment("w1", "a1", body());
        assertRequest(t.requests.get(0), "POST", "/v1/workspaces/w1/scheduling/assignments", true);
        assertRequest(t.requests.get(1), "GET", "/v1/workspaces/w1/scheduling/assignments/all", false);
        assertRequest(t.requests.get(2), "PUT", "/v1/workspaces/w1/scheduling/assignments/a1", true);
        assertRequest(t.requests.get(3), "POST", "/v1/workspaces/w1/scheduling/assignments/a1/copy", true);
    }

    @Test
    void totalsAndRecurringMethodsUseDocumentedPaths() throws Exception {
        RecordingTransport t = new RecordingTransport(8);
        ClockifyClient c = new ClockifyClient(ClockifyClient.builder().apiKey("secret").buildConfig(), t);
        c.scheduling().getAssignmentsPerProjectTotals("w1", body());
        c.scheduling().getProjectAssignmentsTotals("w1", "p1");
        c.scheduling().publishAssignments("w1", body());
        c.scheduling().createRecurringAssignment("w1", body());
        c.scheduling().replaceRecurringAssignment("w1", "a1", body());
        c.scheduling().updateRecurringAssignment("w1", "a1", body());
        c.scheduling().changeRecurringPeriod("w1", "a1", body());
        assertRequest(t.requests.get(0), "POST", "/v1/workspaces/w1/scheduling/assignments/projects/totals", true);
        assertRequest(t.requests.get(1), "GET", "/v1/workspaces/w1/scheduling/assignments/projects/totals/p1", false);
        assertRequest(t.requests.get(2), "PUT", "/v1/workspaces/w1/scheduling/assignments/publish", true);
        assertRequest(t.requests.get(3), "POST", "/v1/workspaces/w1/scheduling/assignments/recurring", true);
        assertRequest(t.requests.get(4), "PUT", "/v1/workspaces/w1/scheduling/assignments/recurring/a1", true);
        assertRequest(t.requests.get(5), "PATCH", "/v1/workspaces/w1/scheduling/assignments/recurring/a1", true);
        assertRequest(t.requests.get(6), "PUT", "/v1/workspaces/w1/scheduling/assignments/series/a1", true);
    }

    @Test
    void userTotalsMethodsUseDocumentedPaths() throws Exception {
        RecordingTransport t = new RecordingTransport(3);
        ClockifyClient c = new ClockifyClient(ClockifyClient.builder().apiKey("secret").buildConfig(), t);
        c.scheduling().getUsersCapacityTotals("w1", body());
        c.scheduling().getAssignmentsUsersTotals("w1", body());
        c.scheduling().getUserCapacityTotal("w1", "u1");
        assertRequest(t.requests.get(0), "POST", "/v1/workspaces/w1/scheduling/assignments/user-filter/totals", true);
        assertRequest(t.requests.get(1), "POST", "/v1/workspaces/w1/scheduling/assignments/users/totals", true);
        assertRequest(t.requests.get(2), "GET", "/v1/workspaces/w1/scheduling/assignments/users/u1/totals", false);
    }

    @Test
    void deleteMethodsUseDocumentedPaths() throws Exception {
        RecordingTransport t = new RecordingTransport(2);
        ClockifyClient c = new ClockifyClient(ClockifyClient.builder().apiKey("secret").buildConfig(), t);
        c.scheduling().deleteRecurringAssignment("w1", "a1");
        c.scheduling().deleteAssignment("w1", "a1");
        assertRequest(t.requests.get(0), "DELETE", "/v1/workspaces/w1/scheduling/assignments/recurring/a1", false);
        assertRequest(t.requests.get(1), "DELETE", "/v1/workspaces/w1/scheduling/assignments/a1", false);
    }

    @Test
    void requiredIdsAndBodiesAreValidated() {
        ClockifyClient c = new ClockifyClient(ClockifyClient.builder().apiKey("secret").buildConfig(), new RecordingTransport(1));
        assertThrows(IllegalArgumentException.class, () -> c.scheduling().getAllAssignments(" "));
        assertThrows(IllegalArgumentException.class, () -> c.scheduling().getUserCapacityTotal("w1", " "));
        assertThrows(NullPointerException.class, () -> c.scheduling().createAssignment("w1", null));
    }

    private com.fasterxml.jackson.databind.JsonNode body() { return objectMapper.createObjectNode().put("marker", true); }

    private static void assertRequest(ClockifyRequest r, String method, String path, boolean hasBody) {
        assertEquals(method, r.method());
        assertEquals(path, r.pathWithQuery());
        assertEquals(ClockifyBaseUrlFamily.BACKEND, r.baseUrlFamily());
        if (hasBody) assertTrue(r.jsonBody().contains("marker")); else assertNull(r.jsonBody());
    }

    static final class RecordingTransport implements ClockifyTransport {
        final List<ClockifyRequest> requests = new ArrayList<>();
        RecordingTransport(int ignored) {}
        public ClockifyResponse<String> send(ClockifyRequest request) { requests.add(request); return new ClockifyResponse<>(200, Map.of(), "{}"); }
        public ClockifyBinaryResponse sendBinary(ClockifyRequest request) { throw new UnsupportedOperationException("not used"); }
    }
}
