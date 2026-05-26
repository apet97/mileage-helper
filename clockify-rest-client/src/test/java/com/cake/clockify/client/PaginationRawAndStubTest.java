package com.cake.clockify.client;

import com.cake.clockify.client.models.Project;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PaginationRawAndStubTest {
    @Test
    void typedUserAndWorkspaceConveniencesMapMinimalDtos() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of(
                new ClockifyResponse<>(200, Map.of(), "{\"id\":\"u1\",\"name\":\"Ada\",\"email\":\"ada@example.test\",\"extra\":true}"),
                new ClockifyResponse<>(200, Map.of(), "[{\"id\":\"w1\",\"name\":\"Workspace\",\"extra\":true}]"),
                new ClockifyResponse<>(200, Map.of(), "{\"id\":\"w1\",\"name\":\"Workspace\"}")
        ));
        ClockifyClient client = TestClockifyClient.client(transport);
        assertEquals("u1", client.users().current().id());
        assertEquals("w1", client.workspaces().list().get(0).id());
        assertEquals("Workspace", client.workspaces().get("w1").name());
    }

    @Test
    void projectListAllStopsAtMaxRowsAndUsesLastPageHeader() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of(
                new ClockifyResponse<>(200, Map.of("Last-Page", List.of("false")), "[{\"id\":\"p1\",\"name\":\"one\"},{\"id\":\"p2\",\"name\":\"two\"}]"),
                new ClockifyResponse<>(200, Map.of("Last-Page", List.of("true")), "[{\"id\":\"p3\",\"name\":\"three\"}]")
        ));
        ClockifyClient client = TestClockifyClient.client(transport);

        List<Project> projects = client.projects().listAll("w1", 3, 2);

        assertEquals(3, projects.size());
        assertEquals("p1", projects.get(0).id());
        assertEquals("/v1/workspaces/w1/projects?page=1&page-size=2", transport.requests.get(0).pathWithQuery());
        assertEquals("/v1/workspaces/w1/projects?page=2&page-size=1", transport.requests.get(1).pathWithQuery());
    }

    @Test
    void rawConvenienceMethodsValidatePathAndBuildQuery() throws Exception {
        RecordingTransport transport = new RecordingTransport(List.of(new ClockifyResponse<>(200, Map.of(), "{}")));
        ClockifyRawClient raw = new ClockifyRawClient(transport);
        raw.get("/v1/workspaces/w1/projects", Map.of("page", 1));
        assertEquals("/v1/workspaces/w1/projects?page=1", transport.requests.get(0).pathWithQuery());
        assertThrows(IllegalArgumentException.class, () -> raw.get("https://api.clockify.me/api/v1/user", Map.of()));
    }

    static final class RecordingTransport implements ClockifyTransport {
        final List<ClockifyResponse<String>> responses;
        final List<ClockifyRequest> requests = new ArrayList<>();
        int index;

        RecordingTransport(List<ClockifyResponse<String>> responses) {
            this.responses = responses;
        }

        @Override
        public ClockifyResponse<String> send(ClockifyRequest request) {
            requests.add(request);
            return responses.get(index++);
        }

        @Override
        public ClockifyBinaryResponse sendBinary(ClockifyRequest request) {
            throw new UnsupportedOperationException("not used");
        }
    }
}
