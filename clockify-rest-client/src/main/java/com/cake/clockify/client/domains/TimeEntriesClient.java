package com.cake.clockify.client.domains;

import com.cake.clockify.client.ClockifyBaseUrlFamily;
import com.cake.clockify.client.ClockifyPageRequest;
import com.cake.clockify.client.ClockifyRequest;
import com.cake.clockify.client.ClockifyTransport;
import com.cake.clockify.client.models.TimeEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.cake.clockify.client.ClockifyPath.segment;

public final class TimeEntriesClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public TimeEntriesClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public List<TimeEntry> getTimeEntries(String workspaceId, String userId, ClockifyPageRequest pageRequest) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        Objects.requireNonNull(pageRequest, "pageRequest");
        ClockifyRequest request = ClockifyRequest.builder("GET", userTimeEntriesPath(workspaceId, userId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .query("page", pageRequest.page())
                .query("page-size", pageRequest.pageSize())
                .build();
        return objectMapper.readValue(transport.send(request).body(), new TypeReference<>() {});
    }

    public List<TimeEntry> getInProgressTimeEntries(String workspaceId, ClockifyPageRequest pageRequest) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        Objects.requireNonNull(pageRequest, "pageRequest");
        ClockifyRequest request = ClockifyRequest.builder("GET", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/time-entries/status/in-progress")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .query("page", pageRequest.page())
                .query("page-size", pageRequest.pageSize())
                .build();
        return objectMapper.readValue(transport.send(request).body(), new TypeReference<>() {});
    }

    public TimeEntry createTimeEntry(String workspaceId, Object requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("POST", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/time-entries", requestBody, TimeEntry.class);
    }

    public TimeEntry getTimeEntry(String workspaceId, String timeEntryId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("timeEntryId", timeEntryId);
        ClockifyRequest request = ClockifyRequest.builder("GET", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/time-entries/" + segment("timeEntryId", timeEntryId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        return objectMapper.readValue(transport.send(request).body(), TimeEntry.class);
    }

    public TimeEntry updateTimeEntry(String workspaceId, String timeEntryId, Object requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("timeEntryId", timeEntryId);
        return sendJson("PUT", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/time-entries/" + segment("timeEntryId", timeEntryId), requestBody, TimeEntry.class);
    }

    public void deleteTimeEntry(String workspaceId, String timeEntryId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("timeEntryId", timeEntryId);
        ClockifyRequest request = ClockifyRequest.builder("DELETE", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/time-entries/" + segment("timeEntryId", timeEntryId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        transport.send(request);
    }

    public List<TimeEntry> markTimeEntriesInvoiced(String workspaceId, Object requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("PATCH", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/time-entries/invoiced", requestBody, new TypeReference<>() {});
    }

    public List<TimeEntry> markTimeEntriesInvoicedBulk(String workspaceId, Object requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        return sendJson("PATCH", "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/time-entries/invoiced/bulk", requestBody, new TypeReference<>() {});
    }

    public TimeEntry createTimeEntryForUser(String workspaceId, String userId, Object requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        return sendJson("POST", userTimeEntriesPath(workspaceId, userId), requestBody, TimeEntry.class);
    }

    public TimeEntry replaceTimeEntriesForUser(String workspaceId, String userId, Object requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        return sendJson("PUT", userTimeEntriesPath(workspaceId, userId), requestBody, TimeEntry.class);
    }

    public TimeEntry patchTimeEntriesForUser(String workspaceId, String userId, Object requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        return sendJson("PATCH", userTimeEntriesPath(workspaceId, userId), requestBody, TimeEntry.class);
    }

    public Object deleteManyTimeEntriesForUser(String workspaceId, String userId) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        ClockifyRequest request = ClockifyRequest.builder("DELETE", userTimeEntriesPath(workspaceId, userId))
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .build();
        return objectMapper.readValue(transport.send(request).body(), Object.class);
    }

    public TimeEntry stopTimerForUser(String workspaceId, String userId, Object requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        return sendJson("PATCH", userTimeEntriesPath(workspaceId, userId) + "/stop", requestBody, TimeEntry.class);
    }

    public TimeEntry duplicateTimeEntry(String workspaceId, String userId, String timeEntryId, Object requestBody) throws IOException, InterruptedException {
        requireId("workspaceId", workspaceId);
        requireId("userId", userId);
        requireId("timeEntryId", timeEntryId);
        return sendJson("POST", userTimeEntriesPath(workspaceId, userId) + "/" + segment("timeEntryId", timeEntryId) + "/duplicate", requestBody, TimeEntry.class);
    }

    private static String userTimeEntriesPath(String workspaceId, String userId) {
        return "/v1/workspaces/" + segment("workspaceId", workspaceId) + "/user/" + segment("userId", userId) + "/time-entries";
    }

    private <T> T sendJson(String method, String path, Object requestBody, Class<T> responseType) throws IOException, InterruptedException {
        Objects.requireNonNull(requestBody, "requestBody");
        ClockifyRequest request = ClockifyRequest.builder(method, path)
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .jsonBody(objectMapper.writeValueAsString(requestBody))
                .build();
        return objectMapper.readValue(transport.send(request).body(), responseType);
    }

    private <T> T sendJson(String method, String path, Object requestBody, TypeReference<T> responseType) throws IOException, InterruptedException {
        Objects.requireNonNull(requestBody, "requestBody");
        ClockifyRequest request = ClockifyRequest.builder(method, path)
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .jsonBody(objectMapper.writeValueAsString(requestBody))
                .build();
        return objectMapper.readValue(transport.send(request).body(), responseType);
    }

    private static void requireId(String name, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
