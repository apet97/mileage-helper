package com.cake.clockify.addon.db;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationTest extends AbstractDbTest {

    @Test
    void testTablesExist() {
        assertTableExists("addon_installations");
        assertTableExists("addon_webhook_tokens");
        assertTableExists("addon_workspace_settings");
        assertTableExists("addon_webhook_events");
    }

    @Test
    void testWorkspaceSettingsUniqueConstraint() {
        // Insert first setting
        jdbcTemplate.update(
                "INSERT INTO " + TEST_SCHEMA + ".addon_workspace_settings " +
                        "(id, addon_key, workspace_id, setting_key, value_json) " +
                        "VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), "my-addon", "ws-1", "theme", "{\"color\":\"dark\"}");

        // Attempting to insert a duplicate (addon_key, workspace_id, setting_key) must throw a DataAccessException
        assertThatThrownBy(() -> {
            jdbcTemplate.update(
                    "INSERT INTO " + TEST_SCHEMA + ".addon_workspace_settings " +
                            "(id, addon_key, workspace_id, setting_key, value_json) " +
                            "VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), "my-addon", "ws-1", "theme", "{\"color\":\"light\"}");
        }).isInstanceOf(DataAccessException.class);
    }

    @Test
    void testWebhookEventsUniqueConstraint() {
        // Insert first event
        jdbcTemplate.update(
                "INSERT INTO " + TEST_SCHEMA + ".addon_webhook_events " +
                        "(id, addon_key, workspace_id, event_type, dedupe_key) " +
                        "VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), "my-addon", "ws-1", "TIME_ENTRY_CREATED", "evt-1234");

        // Attempting to insert duplicate (addon_key, workspace_id, dedupe_key) must throw a DataAccessException
        assertThatThrownBy(() -> {
            jdbcTemplate.update(
                    "INSERT INTO " + TEST_SCHEMA + ".addon_webhook_events " +
                            "(id, addon_key, workspace_id, event_type, dedupe_key) " +
                            "VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), "my-addon", "ws-1", "TIME_ENTRY_CREATED", "evt-1234");
        }).isInstanceOf(DataAccessException.class);
    }

    private void assertTableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?",
                Integer.class,
                TEST_SCHEMA,
                tableName);
        assertThat(count).isEqualTo(1);
    }
}
