package com.cake.clockify.client;

import com.cake.clockify.client.domains.TimeEntriesClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TimeEntriesClientTest {
    @Test
    void getTimeEntriesUsesDocumentedUserScopedPathAndPagination() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        ClockifyClient clockifyClient = TestClockifyClient.client(transport);
        TimeEntriesClient client = clockifyClient.timeEntries();

        assertNotNull(client.getTimeEntries("w1", "u1", new ClockifyPageRequest(3, 25)));

        ClockifyRequest request = transport.requests.get(0);
        assertEquals("GET", request.method());
        assertEquals("/v1/workspaces/w1/user/u1/time-entries?page=3&page-size=25", request.pathWithQuery());
        assertEquals(ClockifyBaseUrlFamily.BACKEND, request.baseUrlFamily());
    }

    @Test
    void getInProgressTimeEntriesUsesDocumentedPathAndPagination() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        TimeEntriesClient client = new TimeEntriesClient(transport, new ObjectMapper());

        assertNotNull(client.getInProgressTimeEntries("w1", new ClockifyPageRequest(2, 10)));

        ClockifyRequest request = transport.requests.get(0);
        assertEquals("GET", request.method());
        assertEquals("/v1/workspaces/w1/time-entries/status/in-progress?page=2&page-size=10", request.pathWithQuery());
        assertEquals(ClockifyBaseUrlFamily.BACKEND, request.baseUrlFamily());
    }

    @Test
    void workspaceScopedCrudUsesDocumentedPaths() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        TimeEntriesClient client = new TimeEntriesClient(transport, new ObjectMapper());
        ObjectMapper mapper = new ObjectMapper();

        client.createTimeEntry("w1", mapper.createObjectNode().put("description", "example"));
        client.getTimeEntry("w1", "te1");
        client.updateTimeEntry("w1", "te1", mapper.createObjectNode().put("description", "updated"));
        client.deleteTimeEntry("w1", "te1");

        assertRequest(transport.requests.get(0), "POST", "/v1/workspaces/w1/time-entries", "description");
        assertRequest(transport.requests.get(1), "GET", "/v1/workspaces/w1/time-entries/te1", null);
        assertRequest(transport.requests.get(2), "PUT", "/v1/workspaces/w1/time-entries/te1", "description");
        assertRequest(transport.requests.get(3), "DELETE", "/v1/workspaces/w1/time-entries/te1", null);
    }

    @Test
    void getTimeEntriesRequiresIdsAndPageRequest() {
        TimeEntriesClient client = new TimeEntriesClient(new RecordingTransport(), new ObjectMapper());
        assertThrows(IllegalArgumentException.class, () -> client.getTimeEntries(" ", "u1", new ClockifyPageRequest(1, 10)));
        assertThrows(IllegalArgumentException.class, () -> client.getTimeEntries("w1", " ", new ClockifyPageRequest(1, 10)));
        assertThrows(NullPointerException.class, () -> client.getTimeEntries("w1", "u1", null));
        assertThrows(NullPointerException.class, () -> client.createTimeEntry("w1", null));
        assertThrows(IllegalArgumentException.class, () -> client.getInProgressTimeEntries(" ", new ClockifyPageRequest(1, 10)));
        assertThrows(NullPointerException.class, () -> client.getInProgressTimeEntries("w1", null));
        assertThrows(IllegalArgumentException.class, () -> client.getTimeEntry("w1", " "));
    }

    private static void assertRequest(ClockifyRequest request, String method, String pathWithQuery, String bodyContains) {
        assertEquals(method, request.method());
        assertEquals(pathWithQuery, request.pathWithQuery());
        assertEquals(ClockifyBaseUrlFamily.BACKEND, request.baseUrlFamily());
        if (bodyContains == null) assertNull(request.jsonBody());
        else assertTrue(request.jsonBody().contains(bodyContains));
    }

    static final class RecordingTransport implements ClockifyTransport {
        final List<ClockifyRequest> requests = new ArrayList<>();

        @Override
        public ClockifyResponse<String> send(ClockifyRequest request) {
            requests.add(request);
            boolean listEndpoint = request.method().equals("GET") && (request.path().contains("/user/") || request.path().contains("/status/in-progress"));
            return new ClockifyResponse<>(200, Map.of(), listEndpoint ? "[]" : "{}");
        }

        @Override
        public ClockifyBinaryResponse sendBinary(ClockifyRequest request) {
            throw new UnsupportedOperationException("not used");
        }
    }
}
