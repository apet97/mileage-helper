package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.db.service.AddonInstallationService;
import com.cake.clockify.addon.mileage.clockify.ClockifyCategoryOption;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.security.MileageAuthorizationService;
import com.cake.clockify.addon.mileage.settings.MileageSettingsService;
import com.cake.clockify.addon.mileage.settings.MileageSettingsValidation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MileageSettingsControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MileageSettingsService settingsService;
    private ClockifyExpenseGateway gateway;
    private AddonInstallationService installationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        settingsService = mock(MileageSettingsService.class);
        gateway = mock(ClockifyExpenseGateway.class);
        installationService = mock(AddonInstallationService.class);
        MileageSettingsController controller = new MileageSettingsController(
                settingsService,
                gateway,
                new MileageAuthorizationService(),
                installationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new MileageExceptionHandler(objectMapper))
                .build();
    }

    @Test
    void adminCanReadSettings() throws Exception {
        when(settingsService.getEffectiveSettings("ws-admin")).thenReturn(settingsResponse(List.of()));

        mockMvc.perform(get("/api/mileage/settings")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value("0.655"))
                .andExpect(jsonPath("$.outputCategoryId").value("cat-output"));
    }

    @Test
    void adminCanSaveSettings() throws Exception {
        when(settingsService.getEffectiveSettings("ws-admin")).thenReturn(settingsResponse(List.of()));

        mockMvc.perform(put("/api/mileage/settings")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rate":"0.70","inputCategoryId":"cat-input","outputCategoryId":"cat-output"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value("0.655"));

        verify(settingsService).saveSettings(eq("ws-admin"), any(), eq("user-claims"));
    }

    @Test
    void memberCannotSaveSettings() throws Exception {
        mockMvc.perform(put("/api/mileage/settings")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListCategoryOptions() throws Exception {
        when(gateway.listCategories("ws-admin")).thenReturn(List.of(
                new ClockifyCategoryOption("cat-unit", "Mileage", "UNIT", "mi", new BigDecimal("0.655")),
                new ClockifyCategoryOption("cat-flat", "Mileage reimbursement", "FLAT", null, null)));

        mockMvc.perform(get("/api/mileage/options/categories")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].type").value("UNIT"))
                .andExpect(jsonPath("$.categories[1].type").value("FLAT"));
    }

    @Test
    void diagnosticsReportsMissingSettings() throws Exception {
        when(settingsService.getEffectiveSettings("ws-admin")).thenReturn(settingsResponse(List.of(
                "rate is required", "outputCategoryId is required")));
        when(installationService.isInstalled("ws-admin")).thenReturn(true);

        mockMvc.perform(get("/api/mileage/diagnostics")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installationAvailable").value(true))
                .andExpect(jsonPath("$.settingsComplete").value(true))
                .andExpect(jsonPath("$.nativeConversionReady").value(false))
                .andExpect(jsonPath("$.warnings[0]").value("rate is required"));
    }

    @Test
    void diagnosticsReportsMissingInstallation() throws Exception {
        when(settingsService.getEffectiveSettings("ws-admin")).thenReturn(settingsResponse(List.of()));
        when(installationService.isInstalled("ws-admin")).thenReturn(false);

        mockMvc.perform(get("/api/mileage/diagnostics")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installationAvailable").value(false))
                .andExpect(jsonPath("$.warnings[0]").value("installation record is missing; reinstall the add-on before publishing or testing native conversion"));
    }

    private static com.cake.clockify.addon.mileage.api.model.MileageSettingsResponse settingsResponse(List<String> diagnostics) {
        return new com.cake.clockify.addon.mileage.api.model.MileageSettingsResponse(
                true, "0.655", "mi", "cat-input", "cat-output", RoundingMode.HALF_UP.name(),
                true, true, true, false, false, null, true, diagnostics.isEmpty(), diagnostics);
    }

    private static NormalizedClaims claims(String role) {
        return new NormalizedClaims("ws-admin", "mileage-for-clockify", "https://backend.example.test",
                "https://reports.example.test", null, null, "user-claims", role, "en", "DEFAULT", "UTC", Instant.now());
    }
}
