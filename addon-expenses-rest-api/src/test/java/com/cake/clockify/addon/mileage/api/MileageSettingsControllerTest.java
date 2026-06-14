package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.db.service.AddonInstallationService;
import com.cake.clockify.addon.mileage.clockify.ClockifyCategoryOption;
import com.cake.clockify.addon.mileage.clockify.ClockifyExpenseGateway;
import com.cake.clockify.addon.mileage.clockify.ClockifyUserOption;
import com.cake.clockify.addon.mileage.security.MileageAuthorizationService;
import com.cake.clockify.addon.mileage.settings.MileageSettingsService;
import com.cake.clockify.client.ClockifyApiException;
import com.cake.clockify.client.ClockifyTransportException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(jsonPath("$.mileageCategoryId").value("cat-mileage"))
                .andExpect(jsonPath("$.mileageCategoryName").value("Mileage"))
                .andExpect(jsonPath("$.fixedUnit").value("mile"))
                .andExpect(jsonPath("$.fixedRoundingMode").value("HALF_UP"));
    }

    @Test
    void adminCanSaveSettings() throws Exception {
        when(settingsService.getEffectiveSettings("ws-admin")).thenReturn(settingsResponse(List.of()));

        mockMvc.perform(put("/api/mileage/settings")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rate":"0.70","mileageCategoryId":"cat-mileage"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value("0.655"));

        verify(settingsService).saveSettings(eq("ws-admin"), any(), eq("user-claims"));
    }

    @Test
    void invalidSettingsRateReturnsBadRequest() throws Exception {
        when(settingsService.saveSettings(eq("ws-admin"), any(), eq("user-claims")))
                .thenThrow(new IllegalArgumentException("rate must be at most 10000"));

        mockMvc.perform(put("/api/mileage/settings")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rate":"10000.000001","mileageCategoryId":"cat-mileage"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.message").value("rate must be at most 10000"));
    }

    @Test
    void savingSettingsSyncsClockifyMileageCategoryPriceToRate() throws Exception {
        when(settingsService.getEffectiveSettings("ws-admin")).thenReturn(settingsResponse(List.of()));

        mockMvc.perform(put("/api/mileage/settings")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rate":"0.655","mileageCategoryId":"cat-mileage"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings").isEmpty());

        // The Clockify Mileage category's unit price must be kept in step with the saved rate so a unit
        // category (total = miles × priceInCents) charges the intended amount.
        verify(gateway).createOrRepairMileageCategory("ws-admin", new BigDecimal("0.655"));
    }

    @Test
    void savingSettingsStillSucceedsWhenCategoryPriceSyncFails() throws Exception {
        when(settingsService.getEffectiveSettings("ws-admin")).thenReturn(settingsResponse(List.of()));
        when(gateway.createOrRepairMileageCategory("ws-admin", new BigDecimal("0.655")))
                .thenThrow(new IOException("clockify unavailable"));

        // A Clockify outage during the best-effort price sync must not fail the settings save.
        mockMvc.perform(put("/api/mileage/settings")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rate":"0.655","mileageCategoryId":"cat-mileage"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value("0.655"))
                .andExpect(jsonPath("$.warnings[0]").value(org.hamcrest.Matchers.containsString("could not be synced")));
    }

    @Test
    void savingSettingsStillSucceedsWhenCategoryPriceSyncHitsTransportTimeout() throws Exception {
        when(settingsService.getEffectiveSettings("ws-admin")).thenReturn(settingsResponse(List.of()));
        // The real production timeout path is a ClockifyTransportException (RuntimeException); the best-effort
        // sync must still not fail the committed save.
        when(gateway.createOrRepairMileageCategory("ws-admin", new BigDecimal("0.655")))
                .thenThrow(new ClockifyTransportException("request timed out", null));

        mockMvc.perform(put("/api/mileage/settings")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rate":"0.655","mileageCategoryId":"cat-mileage"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value("0.655"))
                .andExpect(jsonPath("$.warnings[0]").value(org.hamcrest.Matchers.containsString("could not be synced")));
    }

    @Test
    void savingSettingsStillSucceedsWhenCategoryPriceSyncHitsUnexpectedError() throws Exception {
        when(settingsService.getEffectiveSettings("ws-admin")).thenReturn(settingsResponse(List.of()));
        // An unexpected (non-Clockify) RuntimeException is logged at error but must still NOT fail the already
        // committed save — the save is the user's action; the price sync is a best-effort side-effect.
        when(gateway.createOrRepairMileageCategory("ws-admin", new BigDecimal("0.655")))
                .thenThrow(new IllegalStateException("unexpected bug"));

        mockMvc.perform(put("/api/mileage/settings")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rate":"0.655","mileageCategoryId":"cat-mileage"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value("0.655"))
                .andExpect(jsonPath("$.warnings[0]").value(org.hamcrest.Matchers.containsString("internal error")));
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
    void adminCanCreateOrRepairMileageCategory() throws Exception {
        when(settingsService.getEffectiveSettings("ws-admin")).thenReturn(settingsResponse(List.of()));
        when(gateway.createOrRepairMileageCategory("ws-admin", new BigDecimal("0.655")))
                .thenReturn(new ClockifyCategoryOption("cat-mileage", "Mileage", "UNIT", "mile", new BigDecimal("73")));

        mockMvc.perform(post("/api/mileage/settings/mileage-category")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mileageCategoryId").value("cat-mileage"));

        verify(settingsService).saveMileageCategory("ws-admin", "cat-mileage", "user-claims");
    }

    @Test
    void adminCanUseExistingDefaultMileageCategoryWhenRateIsNotSavedYet() throws Exception {
        when(settingsService.getEffectiveSettings("ws-admin"))
                .thenReturn(incompleteSettingsResponse(), settingsResponse("0.18", "cat-mileage", "Mileage", List.of()));
        when(gateway.findMileageCategory("ws-admin"))
                .thenReturn(Optional.of(new ClockifyCategoryOption("cat-mileage", "Mileage", "UNIT", "mile", new BigDecimal("18"))));

        mockMvc.perform(post("/api/mileage/settings/mileage-category")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value("0.18"))
                .andExpect(jsonPath("$.mileageCategoryId").value("cat-mileage"));

        verify(settingsService).saveMileageCategoryWithRate(
                "ws-admin", "cat-mileage", new BigDecimal("0.18"), "user-claims");
        verify(gateway, never()).createOrRepairMileageCategory(eq("ws-admin"), any());
    }

    @Test
    void memberCannotCreateOrRepairMileageCategory() throws Exception {
        mockMvc.perform(post("/api/mileage/settings/mileage-category")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
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
    void adminCanListUserOptions() throws Exception {
        when(gateway.listUsers("ws-admin")).thenReturn(List.of(
                new ClockifyUserOption("user-1", "Ada Lovelace", "ada@example.test"),
                new ClockifyUserOption("user-2", "Alan Turing", "alan@example.test")));

        mockMvc.perform(get("/api/mileage/options/users")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].id").value("user-1"))
                .andExpect(jsonPath("$.users[0].name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.users[1].id").value("user-2"));
    }

    @Test
    void memberCannotListUserOptions() throws Exception {
        mockMvc.perform(get("/api/mileage/options/users")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("MEMBER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void userOptionsDegradeGracefullyWhenClockifyTimesOut() throws Exception {
        // A raw IOException from the gateway must degrade to an empty list + warning (HTTP 200).
        when(gateway.listUsers("ws-admin")).thenThrow(new IOException("request timed out"));

        mockMvc.perform(get("/api/mileage/options/users")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isEmpty())
                .andExpect(jsonPath("$.warning").value(org.hamcrest.Matchers.containsString("temporarily unavailable")));
    }

    @Test
    void userOptionsDegradeGracefullyOnClockifyTransportTimeout() throws Exception {
        // The real production timeout path: a ClockifyTransportException (RuntimeException) wrapping
        // HttpTimeoutException. The narrowed catch must still degrade this to 200 + warning, not 500.
        when(gateway.listUsers("ws-admin")).thenThrow(new ClockifyTransportException("request timed out", null));

        mockMvc.perform(get("/api/mileage/options/users")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").isEmpty())
                .andExpect(jsonPath("$.warning").value(org.hamcrest.Matchers.containsString("temporarily unavailable")));
    }

    @Test
    void categoryOptionsReturnWarningWhenClockifyDeniesLookup() throws Exception {
        when(gateway.listCategories("ws-admin"))
                .thenThrow(ClockifyApiException.forStatus(403, java.util.Map.of(), "{\"message\":\"Forbidden\"}"));

        mockMvc.perform(get("/api/mileage/options/categories")
                        .requestAttr(RequestAttributes.NORMALIZED_CLAIMS, claims("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories").isEmpty())
                .andExpect(jsonPath("$.warning").value(org.hamcrest.Matchers.containsString("Clockify did not allow")));
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
        return settingsResponse("0.655", "cat-mileage", "Mileage", diagnostics);
    }

    private static com.cake.clockify.addon.mileage.api.model.MileageSettingsResponse incompleteSettingsResponse() {
        return settingsResponse(null, null, null, List.of("rate is required", "outputCategoryId is required"));
    }

    private static com.cake.clockify.addon.mileage.api.model.MileageSettingsResponse settingsResponse(
            String rate,
            String mileageCategoryId,
            String mileageCategoryName,
            List<String> diagnostics) {
        return new com.cake.clockify.addon.mileage.api.model.MileageSettingsResponse(
                true, rate, "mile", mileageCategoryId, mileageCategoryId, RoundingMode.HALF_UP.name(),
                true, true, false, false, false, null, rate != null && mileageCategoryId != null, diagnostics.isEmpty(), diagnostics,
                mileageCategoryId, mileageCategoryName, "mile", RoundingMode.HALF_UP.name());
    }

    private static NormalizedClaims claims(String role) {
        return new NormalizedClaims("ws-admin", "mileage-for-clockify", "https://backend.example.test",
                "https://reports.example.test", null, null, "user-claims", role, "en", "DEFAULT", "UTC", Instant.now());
    }
}
