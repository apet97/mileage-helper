package com.cake.clockify.addon.db.config;

import com.cake.clockify.addon.db.entity.AddonWebhookToken;
import com.cake.clockify.addon.db.repository.AddonWebhookTokenRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaWebhookTokenLookupTest {

    @Test
    void lookupUsesAddonInstallationIdNotManifestKey() {
        AddonWebhookTokenRepository repository = mock(AddonWebhookTokenRepository.class);
        AddonWebhookToken entity = new AddonWebhookToken();
        entity.setWorkspaceId("ws-1");
        entity.setAddonKey("test-addon");
        entity.setAddonId("addon-id-1");
        entity.setPath("/new-time-entry");
        entity.setEventType("NEW_TIME_ENTRY");
        entity.setWebhookTokenEnc("encrypted");
        entity.setTokenKeyId("k1");

        when(repository.findByWorkspaceIdAndAddonIdAndPath("ws-1", "addon-id-1", "/new-time-entry"))
                .thenReturn(Optional.of(entity));

        var result = new JpaWebhookTokenLookup(repository)
                .findByWorkspaceAddonPath("ws-1", "addon-id-1", "/new-time-entry");

        assertThat(result).isPresent();
        assertThat(result.get().addonId()).isEqualTo("addon-id-1");
        verify(repository).findByWorkspaceIdAndAddonIdAndPath("ws-1", "addon-id-1", "/new-time-entry");
    }
}
