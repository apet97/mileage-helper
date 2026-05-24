package com.cake.clockify.addon.core.lifecycle;

import com.cake.clockify.addon.core.auth.NormalizedClaims;

import java.util.List;
import java.util.Map;

/**
 * Add-on-specific lifecycle callbacks. Implement this interface as a Spring component in your
 * add-on and the core {@link LifecycleController} will dispatch to it automatically.
 *
 * <p>All methods are called <em>after</em> JWT signature verification. Claims include
 * the workspace's API URLs extracted from the JWT — never hardcode URLs.
 */
public interface AddonLifecycleHandler {

    /**
     * Called when the add-on is installed in a workspace.
     *
     * @param claims   verified JWT claims including workspaceId, addonId, backendUrl
     * @param payload  raw INSTALLED payload (contains {@code authToken}, {@code webhooks}, etc.)
     */
    default void onInstalled(NormalizedClaims claims, Map<String, Object> payload) {
    }

    /**
     * Called when the add-on is uninstalled from a workspace.
     *
     * @param claims verified JWT claims
     */
    default void onDeleted(NormalizedClaims claims) {
    }

    /**
     * Called when workspace settings are updated.
     *
     * @param claims  verified JWT claims
     * @param updates list of setting update maps ({@code id}, {@code value})
     */
    default void onSettingsUpdated(NormalizedClaims claims, List<Map<String, Object>> updates) {
    }

    /**
     * Called when the add-on status changes (e.g. ACTIVE → SUSPENDED).
     *
     * @param claims verified JWT claims
     * @param status the new status string as delivered by Clockify
     */
    default void onStatusChanged(NormalizedClaims claims, String status) {
    }
}
