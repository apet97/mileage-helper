package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.mileage.api.model.MileageRatePolicyListResponse;
import com.cake.clockify.addon.mileage.api.model.MileageRatePolicyRequest;
import com.cake.clockify.addon.mileage.api.model.MileageRatePolicyResponse;
import com.cake.clockify.addon.mileage.policy.MileageRatePolicyService;
import com.cake.clockify.addon.mileage.security.MileageAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MileageRatePolicyControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MileageRatePolicyService policyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        policyService = mock(MileageRatePolicyService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MileageRatePolicyController(
                        policyService,
                        new MileageAuthorizationService()))
                .setControllerAdvice(new MileageExceptionHandler(objectMapper))
                .build();
    }

    @Test
    void adminCanListWorkspacePolicies() throws Exception {
        when(policyService.listPolicyResponse("ws-admin")).thenReturn(new MileageRatePolicyListResponse(
                List.of(policy("IRS 2026", "0.700", true)),
                null));

        mockMvc.perform(get("/api/mileage/rate-policies")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policies[0].name").value("IRS 2026"))
                .andExpect(jsonPath("$.policies[0].rate").value("0.700"))
                .andExpect(jsonPath("$.policies[0].unit").value("mile"));

        verify(policyService).listPolicyResponse("ws-admin");
    }

    @Test
    void memberCannotListPolicies() throws Exception {
        mockMvc.perform(get("/api/mileage/rate-policies")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreatePolicyForClaimsWorkspaceOnly() throws Exception {
        when(policyService.createPolicy(eq("ws-admin"), any(MileageRatePolicyRequest.class), eq("user-claims")))
                .thenReturn(policy("IRS 2026", "0.700", true));

        mockMvc.perform(post("/api/mileage/rate-policies")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"IRS 2026","rate":"0.700","effectiveFrom":"2026-01-01"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("IRS 2026"));

        verify(policyService).createPolicy(eq("ws-admin"), any(MileageRatePolicyRequest.class), eq("user-claims"));
    }

    @Test
    void invalidPolicyInputReturnsBadRequest() throws Exception {
        when(policyService.createPolicy(eq("ws-admin"), any(MileageRatePolicyRequest.class), eq("user-claims")))
                .thenThrow(new IllegalArgumentException("active rate policies may not overlap"));

        mockMvc.perform(post("/api/mileage/rate-policies")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Overlap","rate":"0.700","effectiveFrom":"2026-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("active rate policies may not overlap"));
    }

    @Test
    void adminCanUpdatePolicy() throws Exception {
        UUID id = UUID.randomUUID();
        when(policyService.updatePolicy(eq("ws-admin"), eq(id), any(MileageRatePolicyRequest.class), eq("user-claims")))
                .thenReturn(policy("Updated", "0.725", true));

        mockMvc.perform(put("/api/mileage/rate-policies/{id}", id)
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated","rate":"0.725","effectiveFrom":"2026-01-01","active":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));

        verify(policyService).updatePolicy(eq("ws-admin"), eq(id), any(MileageRatePolicyRequest.class), eq("user-claims"));
    }

    @Test
    void policyFromAnotherWorkspaceReturnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(policyService.updatePolicy(eq("ws-admin"), eq(id), any(MileageRatePolicyRequest.class), eq("user-claims")))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Rate policy not found"));

        mockMvc.perform(put("/api/mileage/rate-policies/{id}", id)
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Other","rate":"0.725","effectiveFrom":"2026-01-01"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Rate policy not found"));
    }

    @Test
    void deleteSoftDeactivatesPolicy() throws Exception {
        UUID id = UUID.randomUUID();
        when(policyService.deactivatePolicy("ws-admin", id, "user-claims"))
                .thenReturn(policy("Inactive", "0.725", false));

        mockMvc.perform(delete("/api/mileage/rate-policies/{id}", id)
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        verify(policyService).deactivatePolicy("ws-admin", id, "user-claims");
    }

    private static MileageRatePolicyResponse policy(String name, String rate, boolean active) {
        return new MileageRatePolicyResponse(
                UUID.randomUUID(),
                name,
                rate,
                "mile",
                LocalDate.parse("2026-01-01"),
                null,
                active,
                "user-claims",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"));
    }

    private static NormalizedClaims claims(String role) {
        return new NormalizedClaims("ws-admin", "mileage-for-clockify", "https://backend.example.test",
                "https://reports.example.test", null, null, "user-claims", role, "en", "DEFAULT", "UTC", Instant.now());
    }
}
