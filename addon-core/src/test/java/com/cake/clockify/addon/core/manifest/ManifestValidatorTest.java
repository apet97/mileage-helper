package com.cake.clockify.addon.core.manifest;

import com.cake.clockify.addonsdk.clockify.model.ClockifyManifest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManifestValidatorTest {

    @Test
    void rejectsMissingOfficialRequiredFields() throws Exception {
        ClockifyManifest manifest = rawManifest("test-key", "", "", "");

        ManifestValidator validator = new ManifestValidator(manifest);

        assertThatThrownBy(validator::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("name")
                .hasMessageContaining("baseUrl")
                .hasMessageContaining("minimalSubscriptionPlan");
    }

    @Test
    void rejectsProductionHttpBaseUrl() {
        ClockifyManifest manifest = ClockifyManifest.v1_3Builder()
                .key("test-key")
                .name("Test Addon")
                .baseUrl("http://addon.example.com")
                .requireFreePlan()
                .build();

        ManifestValidator validator = new ManifestValidator(manifest);

        assertThatThrownBy(validator::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("baseUrl must use https outside localhost");
    }

    private static ClockifyManifest rawManifest(
            String key,
            String name,
            String baseUrl,
            String minimalSubscriptionPlan) throws Exception {
        Class<?> clazz = Class.forName("com.cake.clockify.addonsdk.clockify.model.v1_3.ClockifyManifest");
        Constructor<?> ctor = clazz.getDeclaredConstructor(
                String.class,
                String.class,
                String.class,
                String.class,
                List.class,
                String.class,
                String.class,
                List.class,
                List.class,
                List.class,
                Object.class);
        ctor.setAccessible(true);
        return (ClockifyManifest) ctor.newInstance(
                key,
                name,
                baseUrl,
                minimalSubscriptionPlan,
                new ArrayList<>(),
                null,
                null,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                null);
    }
}
