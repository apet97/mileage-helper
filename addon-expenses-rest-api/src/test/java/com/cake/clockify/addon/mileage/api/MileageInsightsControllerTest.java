package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.api.model.MileageInsightsResponse;
import com.cake.clockify.addon.mileage.insights.MileageInsightsService;
import com.cake.clockify.addon.mileage.security.MileageAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MileageInsightsControllerTest {
    private MileageInsightsService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(MileageInsightsService.class);
        MileageInsightsController controller = new MileageInsightsController(
                new MileageAuthorizationService(),
                new MileageDateRangeResolver(Clock.fixed(Instant.parse("2026-05-27T12:00:00Z"), ZoneOffset.UTC)),
                service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MileageExceptionHandler(new ObjectMapper().findAndRegisterModules()))
                .build();
        when(service.insights(eq("ws-admin"), any(MileageDateRange.class))).thenReturn(response());
    }

    @Test
    void adminGetsInsightsForWorkspaceDateRange() throws Exception {
        mockMvc.perform(get("/api/mileage/insights")
                        .queryParam("from", "2026-05-01")
                        .queryParam("to", "2026-05-31")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalConvertedMiles").value("15"))
                .andExpect(jsonPath("$.topProjects[0].name").value("North Route"));

        ArgumentCaptor<MileageDateRange> range = ArgumentCaptor.forClass(MileageDateRange.class);
        verify(service).insights(eq("ws-admin"), range.capture());
        assertThat(range.getValue().from()).isEqualTo(LocalDate.parse("2026-05-01"));
        assertThat(range.getValue().to()).isEqualTo(LocalDate.parse("2026-05-31"));
    }

    @Test
    void nonAdminCannotReadInsights() throws Exception {
        mockMvc.perform(get("/api/mileage/insights")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isForbidden());
    }

    private static MileageInsightsResponse response() {
        return new MileageInsightsResponse(
                "15",
                "10.88",
                "10.88",
                1,
                1,
                1,
                List.of(new MileageInsightsResponse.CountItem("CONVERTED", 2)),
                List.of(new MileageInsightsResponse.CountItem("FINALIZED_OR_LOCKED", 1)),
                List.of(new MileageInsightsResponse.TopItem("project-1", "North Route", "10.88", "15", 2)),
                List.of(new MileageInsightsResponse.TopItem("user-1", "Ada Lovelace", "10.88", "15", 2)));
    }

    private static NormalizedClaims claims(String role) {
        return new NormalizedClaims("ws-admin", "mileage-for-clockify", "https://backend.example.test",
                "https://reports.example.test", null, null, "user-claims", role, "en", "DEFAULT", "UTC", Instant.now());
    }
}
