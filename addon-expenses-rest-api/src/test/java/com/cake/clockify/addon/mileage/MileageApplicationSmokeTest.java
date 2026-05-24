package com.cake.clockify.addon.mileage;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.mileage.audit.MileageConversionRepository;
import com.cake.clockify.addon.mileage.lifecycle.MileageLifecycleHandler;
import com.cake.clockify.addon.mileage.settings.MileageSettingsRepository;
import com.cake.clockify.addon.mileage.settings.MileageSettingsService;
import com.cake.clockify.addon.mileage.settings.MileageWorkspaceSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MileageApplicationSmokeTest {
    private MileageSettingsRepository settingsRepository;
    private MileageConversionRepository conversionRepository;
    private MileageSettingsService settingsService;
    private MileageLifecycleHandler handler;

    @BeforeEach
    void setUp() {
        settingsRepository = mock(MileageSettingsRepository.class);
        conversionRepository = mock(MileageConversionRepository.class);
        settingsService = mock(MileageSettingsService.class);
        handler = new MileageLifecycleHandler(settingsRepository, conversionRepository, settingsService);
    }

    @Test
    void installedLifecycleLeavesTokenPersistenceToAddonDb() {
        handler.onInstalled(claims(), Map.of("authToken", "secret-token"));

        verifyNoInteractions(settingsRepository, conversionRepository, settingsService);
    }

    @Test
    void deletedLifecycleDeletesMileageSettingsAndConversionsForWorkspace() {
        handler.onDeleted(claims());

        verify(settingsRepository).deleteById("ws-life");
        verify(conversionRepository).deleteByWorkspaceId("ws-life");
    }

    @Test
    void settingsUpdatedLifecycleSyncsKnownMileageSettings() {
        handler.onSettingsUpdated(claims(), List.of(
                Map.of("id", "rate", "value", "0.70"),
                Map.of("id", "outputCategoryId", "value", "cat-output")));

        ArgumentCaptor<com.cake.clockify.addon.mileage.api.model.MileageSettingsRequest> request =
                ArgumentCaptor.forClass(com.cake.clockify.addon.mileage.api.model.MileageSettingsRequest.class);
        verify(settingsService).saveSettings(eq("ws-life"), request.capture(), eq("user-claims"));
        assertThat(request.getValue().rate()).isEqualTo("0.70");
        assertThat(request.getValue().outputCategoryId()).isEqualTo("cat-output");
    }

    @Test
    void statusChangedInactiveDisablesExistingSettingsRow() {
        MileageWorkspaceSettings settings = settings();
        when(settingsRepository.findById("ws-life")).thenReturn(Optional.of(settings));

        handler.onStatusChanged(claims(), "INACTIVE");

        assertThat(settings.isEnabled()).isFalse();
        verify(settingsRepository).saveAndFlush(settings);
    }

    @Test
    void statusChangedActiveEnablesExistingSettingsRow() {
        MileageWorkspaceSettings settings = settings();
        settings.setEnabled(false);
        when(settingsRepository.findById("ws-life")).thenReturn(Optional.of(settings));

        handler.onStatusChanged(claims(), "ACTIVE");

        assertThat(settings.isEnabled()).isTrue();
        verify(settingsRepository).saveAndFlush(settings);
    }

    @Test
    void statusChangedDoesNotCreateMissingSettingsRow() {
        when(settingsRepository.findById("ws-life")).thenReturn(Optional.empty());

        handler.onStatusChanged(claims(), "INACTIVE");

        verify(settingsRepository, never()).saveAndFlush(any());
    }

    @Test
    void applicationPropertiesUseMileageDefaults() throws Exception {
        String application = Files.readString(Path.of("src/main/resources/application.yaml"));
        String testApplication = Files.readString(Path.of("src/test/resources/application-test.yaml"));

        assertThat(application).contains("name: mileage-for-clockify");
        assertThat(application).contains("ADDON_KEY:mileage-for-clockify");
        assertThat(application).contains("jdbc:postgresql://localhost:5432/mileage_for_clockify");
        assertThat(testApplication).contains("key: mileage-for-clockify");
        assertThat(testApplication).contains("name: Mileage for Clockify");
        assertThat(testApplication).doesNotContain("temp_addon_expenses");
    }

    @Test
    void readmeDoesNotAdvertiseGenericExpensesApiExplorer() throws Exception {
        String readme = Files.readString(Path.of("README.md"));

        assertThat(readme).contains("Mileage for Clockify");
        assertThat(readme).doesNotContain("Managed REST Proxy");
    }

    private static MileageWorkspaceSettings settings() {
        MileageWorkspaceSettings settings = new MileageWorkspaceSettings();
        settings.setWorkspaceId("ws-life");
        settings.setEnabled(true);
        return settings;
    }

    private static NormalizedClaims claims() {
        return new NormalizedClaims("ws-life", "mileage-for-clockify", "https://backend.example.test",
                "https://reports.example.test", null, null, "user-claims", "OWNER", "en", "DEFAULT", "UTC", Instant.now());
    }
}
