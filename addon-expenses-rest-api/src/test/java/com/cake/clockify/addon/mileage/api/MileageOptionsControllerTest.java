package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.clockify.ClockifyProjectOption;
import com.cake.clockify.client.ClockifyTransportException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MileageOptionsControllerTest {
    private ClockifyExpenseGateway gateway;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        gateway = mock(ClockifyExpenseGateway.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MileageOptionsController(gateway))
                .setControllerAdvice(new MileageExceptionHandler(new ObjectMapper().findAndRegisterModules()))
                .build();
    }

    @Test
    void userCanListProjects() throws Exception {
        when(gateway.listProjects("ws-user")).thenReturn(List.of(
                new ClockifyProjectOption("project-1", "Client Visit")));

        mockMvc.perform(get("/api/mileage/options/projects")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects[0].id").value("project-1"))
                .andExpect(jsonPath("$.projects[0].name").value("Client Visit"));
    }

    @Test
    void projectOptionsDegradeGracefullyWhenClockifyTimesOut() throws Exception {
        // A raw IOException from the gateway must degrade to an empty list + warning (HTTP 200).
        when(gateway.listProjects("ws-user")).thenThrow(new java.io.IOException("request timed out"));

        mockMvc.perform(get("/api/mileage/options/projects")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects").isEmpty())
                .andExpect(jsonPath("$.warning").value(org.hamcrest.Matchers.containsString("temporarily unavailable")));
    }

    @Test
    void projectOptionsDegradeGracefullyOnClockifyTransportTimeout() throws Exception {
        // The real production timeout path: HttpTimeoutException is wrapped as a ClockifyTransportException
        // (a RuntimeException). The narrowed catch must still degrade this to 200 + warning, not 500.
        when(gateway.listProjects("ws-user")).thenThrow(new ClockifyTransportException("request timed out", null));

        mockMvc.perform(get("/api/mileage/options/projects")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projects").isEmpty())
                .andExpect(jsonPath("$.warning").value(org.hamcrest.Matchers.containsString("temporarily unavailable")));
    }

    private static NormalizedClaims claims() {
        return new NormalizedClaims("ws-user", "mileage-for-clockify", "https://backend.example.test",
                "https://reports.example.test", null, null, "user-claims", "MEMBER", "en", "DEFAULT", "UTC", Instant.now());
    }
}
