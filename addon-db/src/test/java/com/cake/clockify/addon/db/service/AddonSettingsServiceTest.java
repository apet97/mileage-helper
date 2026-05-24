package com.cake.clockify.addon.db.service;

import com.cake.clockify.addon.db.AbstractDbTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class AddonSettingsServiceTest extends AbstractDbTest {

    @Autowired
    private AddonSettingsService service;

    @Test
    void testPutAndGetSetting() {
        service.putSetting("my-addon", "ws-1", "theme", "{\"color\":\"dark\"}", "JSON", "user-1");

        Optional<String> theme = service.getSetting("my-addon", "ws-1", "theme");
        assertThat(theme).isPresent();
        assertThat(theme.get()).isEqualTo("{\"color\":\"dark\"}");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT value_json, value_key_id FROM " + TEST_SCHEMA + ".addon_workspace_settings " +
                        "WHERE addon_key = ? AND workspace_id = ? AND setting_key = ?",
                "my-addon", "ws-1", "theme");
        assertThat(row.get("value_json")).asString().doesNotContain("dark");
        assertThat(row.get("value_key_id")).isEqualTo("k1");
    }

    @Test
    void testReadsLegacyPlaintextSettingsWhenEncryptionColumnsAreEmpty() {
        jdbcTemplate.update(
                "INSERT INTO " + TEST_SCHEMA + ".addon_workspace_settings " +
                        "(addon_key, workspace_id, setting_key, value_json, value_type, updated_by_user_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                "legacy-addon", "ws-legacy", "plain", "{\"legacy\":true}", "JSON", "user-legacy");

        assertThat(service.getSetting("legacy-addon", "ws-legacy", "plain"))
                .contains("{\"legacy\":true}");
    }

    @Test
    void testPutSettingUpsertsValue() {
        service.putSetting("my-addon", "ws-1", "theme", "{\"color\":\"dark\"}", "JSON", "user-1");
        service.putSetting("my-addon", "ws-1", "theme", "{\"color\":\"light\"}", "JSON", "user-2");

        Optional<String> theme = service.getSetting("my-addon", "ws-1", "theme");
        assertThat(theme).isPresent();
        assertThat(theme.get()).isEqualTo("{\"color\":\"light\"}");
    }

    @Test
    void testGetSettingReturnsEmptyForMissingKey() {
        Optional<String> theme = service.getSetting("my-addon", "ws-1", "non-existent");
        assertThat(theme).isEmpty();
    }

    @Test
    void testPutSettingsBulk() {
        Map<String, String> settings = Map.of(
                "theme", "dark",
                "notifications", "enabled"
        );
        service.putSettings("my-addon", "ws-1", settings, "user-1");

        Optional<String> theme = service.getSetting("my-addon", "ws-1", "theme");
        Optional<String> notifications = service.getSetting("my-addon", "ws-1", "notifications");

        assertThat(theme).isPresent().contains("dark");
        assertThat(notifications).isPresent().contains("enabled");
    }

    @Test
    void testGetSettingsForWorkspaceReturnsMap() {
        service.putSetting("my-addon", "ws-1", "theme", "dark", "STRING", "user-1");
        service.putSetting("my-addon", "ws-1", "notifications", "enabled", "STRING", "user-1");
        service.putSetting("my-addon", "ws-2", "theme", "light", "STRING", "user-1"); // different workspace

        Map<String, String> ws1Settings = service.getSettingsForWorkspace("my-addon", "ws-1");
        assertThat(ws1Settings).hasSize(2)
                .containsEntry("theme", "dark")
                .containsEntry("notifications", "enabled");
    }

    @Test
    void testGetSettingStringStripsQuotes() {
        service.putSetting("ws-1", "prefix", "\"hello\"", "user-1");
        Optional<String> val = service.getSettingString("ws-1", "prefix");
        assertThat(val).isPresent().contains("hello");

        String fallback = service.getSettingString("ws-1", "non-existent", "default");
        assertThat(fallback).isEqualTo("default");
    }
}
