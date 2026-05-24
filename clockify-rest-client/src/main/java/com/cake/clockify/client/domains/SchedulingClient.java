package com.cake.clockify.client.domains;

import com.cake.clockify.client.ClockifyBaseUrlFamily;
import com.cake.clockify.client.ClockifyRequest;
import com.cake.clockify.client.ClockifyTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

import static com.cake.clockify.client.ClockifyPath.segment;

public final class SchedulingClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public SchedulingClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode createAssignment(String workspaceId, JsonNode body) throws IOException, InterruptedException { return sendJson("POST", assignmentsPath(workspaceId), body); }
    public JsonNode getAllAssignments(String workspaceId) throws IOException, InterruptedException { requireId("workspaceId", workspaceId); return readJson(request("GET", assignmentsPath(workspaceId) + "/all")); }
    public JsonNode getAssignmentsPerProjectTotals(String workspaceId, JsonNode body) throws IOException, InterruptedException { return sendJson("POST", assignmentsPath(workspaceId) + "/projects/totals", body); }
    public JsonNode getProjectAssignmentsTotals(String workspaceId, String projectId) throws IOException, InterruptedException { requireId("workspaceId", workspaceId); requireId("projectId", projectId); return readJson(request("GET", assignmentsPath(workspaceId) + "/projects/totals/" + segment("projectId", projectId))); }
    public JsonNode publishAssignments(String workspaceId, JsonNode body) throws IOException, InterruptedException { return sendJson("PUT", assignmentsPath(workspaceId) + "/publish", body); }
    public JsonNode createRecurringAssignment(String workspaceId, JsonNode body) throws IOException, InterruptedException { return sendJson("POST", assignmentsPath(workspaceId) + "/recurring", body); }
    public JsonNode replaceRecurringAssignment(String workspaceId, String assignmentId, JsonNode body) throws IOException, InterruptedException { requireId("assignmentId", assignmentId); return sendJson("PUT", assignmentsPath(workspaceId) + "/recurring/" + segment("assignmentId", assignmentId), body); }
    public JsonNode updateRecurringAssignment(String workspaceId, String assignmentId, JsonNode body) throws IOException, InterruptedException { requireId("assignmentId", assignmentId); return sendJson("PATCH", assignmentsPath(workspaceId) + "/recurring/" + segment("assignmentId", assignmentId), body); }
    public void deleteRecurringAssignment(String workspaceId, String assignmentId) throws IOException, InterruptedException { requireId("assignmentId", assignmentId); transport.send(request("DELETE", assignmentsPath(workspaceId) + "/recurring/" + segment("assignmentId", assignmentId))); }
    public JsonNode changeRecurringPeriod(String workspaceId, String assignmentId, JsonNode body) throws IOException, InterruptedException { requireId("assignmentId", assignmentId); return sendJson("PUT", assignmentsPath(workspaceId) + "/series/" + segment("assignmentId", assignmentId), body); }
    public JsonNode getUsersCapacityTotals(String workspaceId, JsonNode body) throws IOException, InterruptedException { return sendJson("POST", assignmentsPath(workspaceId) + "/user-filter/totals", body); }
    public JsonNode getAssignmentsUsersTotals(String workspaceId, JsonNode body) throws IOException, InterruptedException { return sendJson("POST", assignmentsPath(workspaceId) + "/users/totals", body); }
    public JsonNode getUserCapacityTotal(String workspaceId, String userId) throws IOException, InterruptedException { requireId("workspaceId", workspaceId); requireId("userId", userId); return readJson(request("GET", assignmentsPath(workspaceId) + "/users/" + segment("userId", userId) + "/totals")); }
    public JsonNode replaceAssignment(String workspaceId, String assignmentId, JsonNode body) throws IOException, InterruptedException { requireId("assignmentId", assignmentId); return sendJson("PUT", assignmentsPath(workspaceId) + "/" + segment("assignmentId", assignmentId), body); }
    public void deleteAssignment(String workspaceId, String assignmentId) throws IOException, InterruptedException { requireId("assignmentId", assignmentId); transport.send(request("DELETE", assignmentsPath(workspaceId) + "/" + segment("assignmentId", assignmentId))); }
    public JsonNode copyAssignment(String workspaceId, String assignmentId, JsonNode body) throws IOException, InterruptedException { requireId("assignmentId", assignmentId); return sendJson("POST", assignmentsPath(workspaceId) + "/" + segment("assignmentId", assignmentId) + "/copy", body); }

    private JsonNode sendJson(String method, String path, JsonNode body) throws IOException, InterruptedException {
        Objects.requireNonNull(body, "requestBody");
        return readJson(ClockifyRequest.builder(method, path).baseUrlFamily(ClockifyBaseUrlFamily.BACKEND).jsonBody(objectMapper.writeValueAsString(body)).build());
    }

    private ClockifyRequest request(String method, String path) {
        return ClockifyRequest.builder(method, path).baseUrlFamily(ClockifyBaseUrlFamily.BACKEND).build();
    }

    private JsonNode readJson(ClockifyRequest request) throws IOException, InterruptedException {
        return objectMapper.readTree(transport.send(request).body());
    }

    private static String assignmentsPath(String workspaceId) {
        requireId("workspaceId", workspaceId);
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/scheduling/assignments";
    }

    private static void requireId(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
