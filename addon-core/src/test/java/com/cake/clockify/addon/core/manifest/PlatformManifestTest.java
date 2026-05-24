package com.cake.clockify.addon.core.manifest;

import com.cake.clockify.addonsdk.clockify.model.ClockifyManifest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformManifestTest {

    @Test
    void enrichIsIdempotent() {
        ClockifyManifest manifest = ClockifyManifest.v1_3Builder()
                .key("test-key")
                .name("Test Addon")
                .baseUrl("https://addon.example.com")
                .requireFreePlan()
                .build();

        PlatformManifest.enrich(manifest);
        PlatformManifest.enrich(manifest);

        assertThat(manifest.getLifecycle()).hasSize(4);
    }
}
