package com.cake.clockify.addon.db;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for database integration tests using local PostgreSQL instance.
 */
@SpringBootTest(classes = TestDbApplication.class)
@ActiveProfiles("test")
public abstract class AbstractDbTest {

    protected static final String TEST_SCHEMA = "addon_db_test";

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        // Delete in correct order to respect foreign key constraints
        jdbcTemplate.execute("DELETE FROM " + TEST_SCHEMA + ".addon_webhook_tokens");
        jdbcTemplate.execute("DELETE FROM " + TEST_SCHEMA + ".addon_workspace_settings");
        jdbcTemplate.execute("DELETE FROM " + TEST_SCHEMA + ".addon_webhook_events");
        jdbcTemplate.execute("DELETE FROM " + TEST_SCHEMA + ".addon_installations");
    }
}
