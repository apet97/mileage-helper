package com.cake.clockify.addon.core.manifest;

import com.cake.clockify.addonsdk.clockify.model.ClockifyManifest;
import com.cake.clockify.addonsdk.clockify.model.v1_3.ClockifyLifecycleEvent;

import java.util.List;

/**
 * Utility for enhancing a {@link ClockifyManifest}.
 * Automatically configures mandatory platform lifecycle endpoints.
 */
public class PlatformManifest {

    /**
     * Enriches the provided manifest with the platform's mandatory lifecycle webhooks.
     *
     * @param manifest the built manifest to enrich
     * @return the enriched manifest
     */
    @SuppressWarnings("unchecked")
    public static ClockifyManifest enrich(ClockifyManifest manifest) {
        addLifecycleIfMissing(manifest.getLifecycle(), "/lifecycle/installed",
                ClockifyLifecycleEvent.builder().path("/lifecycle/installed").onInstalled().build());
        addLifecycleIfMissing(manifest.getLifecycle(), "/lifecycle/deleted",
                ClockifyLifecycleEvent.builder().path("/lifecycle/deleted").onDeleted().build());
        addLifecycleIfMissing(manifest.getLifecycle(), "/lifecycle/settings-updated",
                ClockifyLifecycleEvent.builder().path("/lifecycle/settings-updated").onSettingsUpdated().build());
        addLifecycleIfMissing(manifest.getLifecycle(), "/lifecycle/status-changed",
                ClockifyLifecycleEvent.builder().path("/lifecycle/status-changed").onStatusChanged().build());

        return manifest;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addLifecycleIfMissing(List lifecycle, String path, ClockifyLifecycleEvent event) {
        if (lifecycle.stream().noneMatch(existing -> path.equals(readPath(existing)))) {
            lifecycle.add(event);
        }
    }

    private static String readPath(Object event) {
        try {
            Object value = event.getClass().getMethod("getPath").invoke(event);
            return value instanceof String s ? s : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
