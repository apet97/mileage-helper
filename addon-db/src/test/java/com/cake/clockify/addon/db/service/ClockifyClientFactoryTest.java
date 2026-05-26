package com.cake.clockify.addon.db.service;

import com.cake.clockify.addon.db.entity.AddonInstallation;
import com.cake.clockify.client.ClockifyBaseUrlFamily;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClockifyClientFactoryTest {

    @Test
    void getClientDoesNotFallbackWhenReportsUrlIsMissing() {
        AddonInstallationService installationService = mock(AddonInstallationService.class);
        AddonInstallation installation = installation("ws-1");
        installation.setReportsUrl(null);
        when(installationService.getRequiredInstallation("ws-1")).thenReturn(installation);
        when(installationService.decryptToken(installation)).thenReturn("installation-token");

        var client = new ClockifyClientFactory(installationService).getClient("ws-1");

        assertThat(client.config().backendBaseUrl()).isEqualTo(URI.create("https://regional.clockify.me/api"));
        assertThat(client.config().reportsBaseUrl()).isNull();
        assertThatThrownBy(() -> client.config().baseUrl(ClockifyBaseUrlFamily.REPORTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("reportsBaseUrl is required for reports requests");
    }

    @Test
    void optionalClientDoesNotFallbackWhenReportsUrlIsMissing() {
        AddonInstallationService installationService = mock(AddonInstallationService.class);
        AddonInstallation installation = installation("ws-1");
        installation.setReportsUrl(null);
        when(installationService.findInstallation("ws-1")).thenReturn(Optional.of(installation));
        when(installationService.decryptToken(installation)).thenReturn("installation-token");

        var client = new ClockifyClientFactory(installationService).getClientOptional("ws-1").orElseThrow();

        assertThat(client.config().backendBaseUrl()).isEqualTo(URI.create("https://regional.clockify.me/api"));
        assertThat(client.config().reportsBaseUrl()).isNull();
        assertThatThrownBy(() -> client.config().baseUrl(ClockifyBaseUrlFamily.REPORTS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("reportsBaseUrl is required for reports requests");
    }

    @Test
    void clientUsesInstalledBackendAndReportsUrls() {
        AddonInstallationService installationService = mock(AddonInstallationService.class);
        AddonInstallation installation = installation("ws-1");
        when(installationService.getRequiredInstallation("ws-1")).thenReturn(installation);
        when(installationService.decryptToken(installation)).thenReturn("installation-token");

        var client = new ClockifyClientFactory(installationService).getClient("ws-1");

        assertThat(client.config().backendBaseUrl()).isEqualTo(URI.create("https://regional.clockify.me/api"));
        assertThat(client.config().reportsBaseUrl()).isEqualTo(URI.create("https://regional.clockify.me/report"));
    }

    private static AddonInstallation installation(String workspaceId) {
        AddonInstallation installation = new AddonInstallation();
        installation.setWorkspaceId(workspaceId);
        installation.setAddonKey("test-addon");
        installation.setAddonId("addon-id-1");
        installation.setBackendUrl("https://regional.clockify.me/api");
        installation.setReportsUrl("https://regional.clockify.me/report");
        installation.setInstallationTokenEnc("encrypted");
        installation.setTokenKeyId("k1");
        installation.setStatus("ACTIVE");
        return installation;
    }
}
