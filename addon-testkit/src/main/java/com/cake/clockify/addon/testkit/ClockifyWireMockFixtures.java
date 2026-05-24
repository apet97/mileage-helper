package com.cake.clockify.addon.testkit;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class ClockifyWireMockFixtures {

    public static MappingBuilder mockWorkspaceFetch(String workspaceId, String name) {
        return get(urlPathEqualTo("/api/v1/workspaces/" + workspaceId))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody("""
                                {
                                  "id": "%s",
                                  "name": "%s"
                                }
                                """.formatted(workspaceId, name)));
    }

    public static MappingBuilder mockTimeEntryCreated(String workspaceId, String entryId, String description) {
        return post(urlPathEqualTo("/api/v1/workspaces/" + workspaceId + "/time-entries"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(201)
                        .withBody("""
                                {
                                  "id": "%s",
                                  "workspaceId": "%s",
                                  "description": "%s"
                                }
                                """.formatted(entryId, workspaceId, description)));
    }

    public static MappingBuilder mockCurrentUser(String userId, String name, String email) {
        return get(urlPathEqualTo("/api/v1/user"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody("""
                                {
                                  "id": "%s",
                                  "name": "%s",
                                  "email": "%s"
                                }
                                """.formatted(userId, name, email)));
    }
}
