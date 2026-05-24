package com.cake.clockify.addon.db.lifecycle;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.config.ClockifyAddonProperties;
import com.cake.clockify.addon.core.crypto.TokenCodec;
import com.cake.clockify.addon.db.entity.AddonWebhookToken;
import com.cake.clockify.addon.db.repository.AddonWebhookTokenRepository;
import com.cake.clockify.addon.db.service.AddonInstallationService;
import com.cake.clockify.addon.db.service.AddonSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaPersistenceLifecycleHandlerTest {

    private AddonInstallationService installationService;
    private AddonWebhookTokenRepository webhookTokenRepository;
    private JpaPersistenceLifecycleHandler handler;

    @BeforeEach
    void setUp() {
        installationService = mock(AddonInstallationService.class);
        webhookTokenRepository = mock(AddonWebhookTokenRepository.class);
        TokenCodec codec = new TokenCodec(
                Map.of("k1", "00000000000000000000000000000000000000000000000000000000000000aa"),
                "k1");
        ClockifyAddonProperties props = new ClockifyAddonProperties(
                "test-addon", "Test Addon", "https://example.com", "Test",
                true, "", "", null);
        handler = new JpaPersistenceLifecycleHandler(
                installationService,
                webhookTokenRepository,
                codec,
                props,
                mock(AddonSettingsService.class),
                new ObjectMapper());
    }

    @Test
    void installedPayloadPersistsFullWebhookUrlAsBareWebhookPathAndWebhookEvent() {
        NormalizedClaims claims = claims();
        Map<String, Object> payload = Map.of(
                "authToken", "installation-token",
                "webhooks", List.of(Map.of(
                        "path", "https://example.com/webhook/new-time-entry",
                        "webhookEvent", "NEW_TIME_ENTRY",
                        "authToken", "webhook-token")));

        when(webhookTokenRepository.findByWorkspaceIdAndAddonKeyAndPath("ws-1", "test-addon", "/new-time-entry"))
                .thenReturn(Optional.empty());

        handler.onInstalled(claims, payload);

        verify(installationService).markInstalled(
                "ws-1", "test-addon", "addon-id-1", "installation-token",
                "https://api.clockify.me/api", "https://reports.api.clockify.me", "user-1");

        ArgumentCaptor<AddonWebhookToken> saved = ArgumentCaptor.forClass(AddonWebhookToken.class);
        verify(webhookTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getWorkspaceId()).isEqualTo("ws-1");
        assertThat(saved.getValue().getAddonKey()).isEqualTo("test-addon");
        assertThat(saved.getValue().getAddonId()).isEqualTo("addon-id-1");
        assertThat(saved.getValue().getPath()).isEqualTo("/new-time-entry");
        assertThat(saved.getValue().getEventType()).isEqualTo("NEW_TIME_ENTRY");
        assertThat(saved.getValue().getWebhookTokenEnc()).isNotEqualTo("webhook-token");
    }

    @Test
    void installedPayloadUpdatesExistingWebhookTokenByNormalizedPath() {
        NormalizedClaims claims = claims();
        AddonWebhookToken existing = new AddonWebhookToken();
        existing.setWorkspaceId("ws-1");
        existing.setAddonKey("test-addon");
        existing.setPath("/new-time-entry");

        when(webhookTokenRepository.findByWorkspaceIdAndAddonKeyAndPath("ws-1", "test-addon", "/new-time-entry"))
                .thenReturn(Optional.of(existing));

        handler.onInstalled(claims, Map.of(
                "authToken", "installation-token",
                "webhooks", List.of(Map.of(
                        "path", "/webhook/new-time-entry",
                        "eventType", "NEW_TIME_ENTRY",
                        "authToken", "rotated-webhook-token"))));

        verify(webhookTokenRepository).save(existing);
        assertThat(existing.getAddonId()).isEqualTo("addon-id-1");
        assertThat(existing.getPath()).isEqualTo("/new-time-entry");
        assertThat(existing.getEventType()).isEqualTo("NEW_TIME_ENTRY");
        assertThat(existing.getWebhookTokenEnc()).isNotBlank();
    }

    @Test
    void installedPayloadAcceptsOfficialWebhookTypeAlias() {
        NormalizedClaims claims = claims();
        Map<String, Object> payload = Map.of(
                "authToken", "installation-token",
                "webhooks", List.of(Map.of(
                        "path", "/webhook/new-time-entry",
                        "webhookType", "NEW_TIME_ENTRY",
                        "authToken", "webhook-token")));

        when(webhookTokenRepository.findByWorkspaceIdAndAddonKeyAndPath("ws-1", "test-addon", "/new-time-entry"))
                .thenReturn(Optional.empty());

        handler.onInstalled(claims, payload);

        ArgumentCaptor<AddonWebhookToken> saved = ArgumentCaptor.forClass(AddonWebhookToken.class);
        verify(webhookTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getEventType()).isEqualTo("NEW_TIME_ENTRY");
    }

    @SuppressWarnings("unchecked")
    @Test
    void installedPayloadIgnoresMalformedWebhookEntries() {
        handler.onInstalled(claims(), Map.of(
                "authToken", "installation-token",
                "webhooks", List.of(
                        Map.of("path", "/webhook/new-time-entry"),
                        Map.of("authToken", "webhook-token"),
                        "not-a-map")));

        verify(webhookTokenRepository, org.mockito.Mockito.never()).save(any());
    }

    private static NormalizedClaims claims() {
        return new NormalizedClaims(
                "ws-1",
                "addon-id-1",
                "https://api.clockify.me/api",
                "https://reports.api.clockify.me",
                null,
                null,
                "user-1",
                null,
                null,
                null,
                null,
                null);
    }
}
