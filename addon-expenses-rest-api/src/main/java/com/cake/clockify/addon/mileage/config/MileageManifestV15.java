package com.cake.clockify.addon.mileage.config;

import com.cake.clockify.addon.core.config.ClockifyAddonProperties;
import com.cake.clockify.addonsdk.clockify.model.ClockifyManifest;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class MileageManifestV15 implements ClockifyManifest {
    private final String schemaVersion;
    private final String key;
    private final String name;
    private final String baseUrl;
    private final String minimalSubscriptionPlan;
    private final String description;
    private final String iconPath;
    private final List<String> scopes;
    private final List<Lifecycle> lifecycle;
    private final List<Webhook> webhooks;
    private final List<Component> components;
    private Object settings;

    private MileageManifestV15(String key, String name, String baseUrl, String description) {
        this.schemaVersion = "1.5";
        this.key = key;
        this.name = name;
        this.baseUrl = baseUrl;
        this.minimalSubscriptionPlan = "PRO";
        this.description = description;
        this.iconPath = "/assets/mileage/icon.png";
        this.scopes = new ArrayList<>(List.of(
                "EXPENSE_READ",
                "EXPENSE_WRITE",
                "USER_READ",
                "PROJECT_READ",
                "WORKSPACE_READ"));
        this.lifecycle = new ArrayList<>(List.of(
                new Lifecycle("/lifecycle/installed", "INSTALLED"),
                new Lifecycle("/lifecycle/deleted", "DELETED"),
                new Lifecycle("/lifecycle/settings-updated", "SETTINGS_UPDATED"),
                new Lifecycle("/lifecycle/status-changed", "STATUS_CHANGED")));
        this.webhooks = new ArrayList<>(List.of(
                new Webhook("EXPENSE_CREATED", "/webhook/expense-created"),
                new Webhook("EXPENSE_UPDATED", "/webhook/expense-updated"),
                new Webhook("EXPENSE_DELETED", "/webhook/expense-deleted"),
                new Webhook("EXPENSE_RESTORED", "/webhook/expense-restored")));
        this.components = new ArrayList<>(List.of(
                new Component("sidebar", "Mileage", "EVERYONE", "/iframe/mileage", "/assets/mileage/icon.png")));
        this.settings = "/iframe/settings";
    }

    public static MileageManifestV15 from(ClockifyAddonProperties props) {
        return new MileageManifestV15(props.key(), props.name(), props.baseUrl(), props.description());
    }

    @Override
    public String getSchemaVersion() {
        return schemaVersion;
    }

    @Override
    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getMinimalSubscriptionPlan() {
        return minimalSubscriptionPlan;
    }

    public String getDescription() {
        return description;
    }

    public String getIconPath() {
        return iconPath;
    }

    public List<String> getScopes() {
        return scopes;
    }

    @Override
    public List<Lifecycle> getLifecycle() {
        return lifecycle;
    }

    @Override
    public List<Webhook> getWebhooks() {
        return webhooks;
    }

    @Override
    public List<Component> getComponents() {
        return components;
    }

    public Object getSettings() {
        return settings;
    }

    @Override
    public void setSettings(Object settings) {
        this.settings = settings;
    }

    public record Lifecycle(String path, String type) {
    }

    public record Webhook(String event, String path) {
    }

    public record Component(String type, String label, String accessLevel, String path, String iconPath) {
    }
}
