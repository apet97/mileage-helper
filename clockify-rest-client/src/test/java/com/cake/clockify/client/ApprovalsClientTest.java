package com.cake.clockify.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalsClientTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void approvalMethodsUseDocumentedPaths() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of("[]", "{}", "{}", "{}", "{}", "{}"));
        ClockifyClient client = TestClockifyClient.client(transport);

        client.approvals().getApprovalRequests("w1", new ClockifyPageRequest(2, 25));
        client.approvals().createApprovalRequest("w1", body());
        client.approvals().resubmitApprovalRequest("w1", body());
        client.approvals().createApprovalForOther("w1", "u1", body());
        client.approvals().resubmitApprovalRequestForOther("w1", "u1", body());
        client.approvals().updateApprovalStatus("w1", "a1", body());

        assertRequest(transport.requests.get(0), "GET", "/v1/workspaces/w1/approval-requests?page=2&page-size=25", null);
        assertRequest(transport.requests.get(1), "POST", "/v1/workspaces/w1/approval-requests", "marker");
        assertRequest(transport.requests.get(2), "POST", "/v1/workspaces/w1/approval-requests/resubmit-entries-for-approval", "marker");
        assertRequest(transport.requests.get(3), "POST", "/v1/workspaces/w1/approval-requests/users/u1", "marker");
        assertRequest(transport.requests.get(4), "POST", "/v1/workspaces/w1/approval-requests/users/u1/resubmit-entries-for-approval", "marker");
        assertRequest(transport.requests.get(5), "PATCH", "/v1/workspaces/w1/approval-requests/a1", "marker");
    }

    @Test
    void requiredIdsAndBodiesAreValidated() {
        ClockifyClient client = TestClockifyClient.client(new RecordingTransport(List.of()));
        assertThrows(IllegalArgumentException.class, () -> client.approvals().getApprovalRequests(" ", new ClockifyPageRequest(1, 10)));
        assertThrows(NullPointerException.class, () -> client.approvals().getApprovalRequests("w1", null));
        assertThrows(IllegalArgumentException.class, () -> client.approvals().createApprovalForOther("w1", " ", body()));
        assertThrows(NullPointerException.class, () -> client.approvals().createApprovalRequest("w1", null));
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
