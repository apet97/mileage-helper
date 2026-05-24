# Mileage for Clockify Implementation Plan

> **Status:** Historical implementation record. Mileage for Clockify has been implemented and extracted into a standalone repository. Keep this file for auditability and traceability, but do not treat unchecked boxes below as the current task queue. For active guidance, use `../../AGENTS.md`, `../../CLAUDE.md`, `../README.md`, and the current source/tests.

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build "Mileage for Clockify" from the current `clockify-expenses-api` Spring Boot boilerplate so users can create precise mileage reimbursements and convert native/mobile unit-based mileage expenses into real Clockify flat expenses.

**Architecture:** Keep Clockify as the source of truth for expenses, receipts, reports, approvals, budgets, and invoices. The add-on stores only workspace settings plus conversion audit/idempotency rows, uses `BigDecimal` for every mileage/rate/money value, and delegates Clockify API calls through the existing `ClockifyClientFactory` so API URLs come from installation/token context.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Maven, addon-core, addon-db, clockify-rest-client, addon-testkit, PostgreSQL, Flyway, JUnit 5, AssertJ, Mockito, Testcontainers, Java JSON Schema Validator `com.github.java-json-tools:json-schema-validator:2.2.14` for draft-04 manifest validation.

---

## 0. Verified Repo Facts

- Work directory for this add-on: `/Users/15x/Downloads/WORKING/addons-me/addonFactory/addon-expenses-rest-api`.
- Maven aggregator root for commands in this plan: `/Users/15x/Downloads/WORKING/addons-me/addonFactory`.
- Current add-on module path: `addon-expenses-rest-api`.
- Current add-on artifact: `clockify-expenses-api`.
- Current add-on Java package: `com.cake.clockify.addon.expenses`.
- Parent Maven project declares modules `addon-core`, `addon-db`, `clockify-rest-client`, `addon-starter`, `addon-testkit`, `addon-break-compliance`, `addon-estimate-guard`, and `addon-expenses-rest-api`.
- Parent Maven property `addon-sdk.version` is `1.5.3`.
- Local cloned official SDK under `addon-expenses-rest-api/addon-java-sdk/` is read-only.
- Official SDK clone exposes `ClockifyManifest.v1_2Builder()`, `ClockifyManifest.v1_3Builder()`, and `ClockifyManifest.v1_4Builder()`.
- Official SDK clone does not expose `ClockifyManifest.v1_5Builder()`.
- Local `manifest-schema.json` has `"version": "1.5"`, includes the expense webhook events, and requires `key`, `name`, `baseUrl`, and `minimalSubscriptionPlan`.
- `PlatformManifest.enrich()` currently uses SDK v1.3 lifecycle classes; do not depend on it for schema 1.5 output.
- `addon-core` auto-wires lifecycle, iframe auth, webhook auth, security headers, `/manifest`, `/healthz`, and `/actuator/health` when an add-on provides a `ClockifyManifest` bean.
- `addon-db` persists installation tokens and webhook tokens through `JpaPersistenceLifecycleHandler`; do not manually persist lifecycle tokens in add-on code.
- `ClockifyClientFactory.getClient(workspaceId)` builds a Clockify client from the encrypted installation token and stored `backendUrl`/`reportsUrl`; use it for server-side Clockify API calls.

## 1. Manifest Schema 1.5 Strategy

Use an add-on-local manual implementation of `com.cake.clockify.addonsdk.clockify.model.ClockifyManifest` named `com.cake.clockify.addon.mileage.config.MileageManifestV15`.

Do not edit `addon-java-sdk/`.

Do not edit `addon-core`.

Do not downgrade to schema 1.4.

The local manual class must:

- Return `schemaVersion` as `"1.5"`.
- Implement SDK interface methods `getSchemaVersion()`, `getKey()`, `getLifecycle()`, `getWebhooks()`, `getComponents()`, and `setSettings(Object settings)`.
- Provide public getters used by `ManifestValidator` reflection: `getName()`, `getBaseUrl()`, `getMinimalSubscriptionPlan()`, and `getScopes()`.
- Return serializable lists made of small records or maps for lifecycle, webhooks, components, and settings.
- Include lifecycle paths `/lifecycle/installed`, `/lifecycle/deleted`, `/lifecycle/settings-updated`, and `/lifecycle/status-changed`.
- Include webhooks `EXPENSE_CREATED`, `EXPENSE_UPDATED`, `EXPENSE_DELETED`, and `EXPENSE_RESTORED`.
- Include sidebar component path `/iframe/mileage`.
- Include self-hosted settings path `/iframe/settings`.
- Use `minimalSubscriptionPlan` value `"PRO"`.

The existing `addon-core` `ManifestController` can serialize this manual `ClockifyManifest` bean without SDK v1.5 generated classes.

## 2. File Map

### 2.1 Modify Existing Root Files

- Modify `addon-expenses-rest-api/pom.xml`: rename artifact/name/description and add test-scope JSON schema validator dependency.
- Modify `addon-expenses-rest-api/README.md`: replace generic Expenses API README with Mileage for Clockify setup, local run, and validation commands.
- Modify `addon-expenses-rest-api/Dockerfile`: change jar copy pattern from `clockify-expenses-api-*.jar` to `mileage-for-clockify-*.jar`.
- Modify `addon-expenses-rest-api/docker-compose.yml`: rename containers, database name, `SPRING_DATASOURCE_URL`, `ADDON_KEY`, `ADDON_NAME`, and `ADDON_DESCRIPTION`.
- Modify `addon-expenses-rest-api/src/main/resources/application.yaml`: change Spring app name, datasource default database, addon defaults, and static product labels.
- Modify `addon-expenses-rest-api/src/test/resources/application-test.yaml`: change addon defaults to Mileage values.
- Keep `addon-expenses-rest-api/manifest-schema.json` unchanged; use it in tests.

### 2.2 Delete Current Generic Expense API Java Files

Delete these files after replacement tests are written:

- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/ClockifyExpensesApiAddonApplication.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/ExpenseCategoryController.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/ExpenseController.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/ExpensesExceptionHandler.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/CategoryStatusRequest.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/CreateCategoryRequest.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/CreateExpenseRequest.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/DetailedReportRequest.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/Expense.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/ExpenseCategory.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/ExpenseCategoryListResponse.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/ExpenseListResponse.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/ExpenseReference.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/UpdateCategoryRequest.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/UpdateExpenseRequest.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/config/ClockifyExpensesApiDbConfig.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/config/ClockifyExpensesApiManifestConfig.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/iframe/SettingsIframeController.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/lifecycle/ClockifyExpensesApiLifecycleHandler.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/webhook/ClockifyExpensesApiExpenseLog.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/webhook/ClockifyExpensesApiExpenseLogRepository.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/webhook/ExpenseCreatedHandler.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/webhook/ExpenseDeletedHandler.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/webhook/ExpenseLogService.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/webhook/ExpenseRestoredHandler.java`
- `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/webhook/ExpenseUpdatedHandler.java`

### 2.3 Delete Current Generic Expense API Tests

Delete these files after replacement tests are written:

- `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/expenses/ClockifyExpensesApiIntegrationTest.java`
- `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/expenses/ClockifyExpensesApiSecurityTest.java`
- `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/expenses/ExpenseApiAdversarialTest.java`
- `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/expenses/ExpenseApiTest.java`
- `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/expenses/ManifestTest.java`

### 2.4 Delete Current Generic Static Assets

- Delete `addon-expenses-rest-api/src/main/resources/static/assets/clockify-expenses-api/settings.css`.
- Delete `addon-expenses-rest-api/src/main/resources/static/assets/clockify-expenses-api/settings.js`.
- Delete empty directory `addon-expenses-rest-api/src/main/resources/static/assets/clockify-expenses-api/` after both files are removed.

### 2.5 Keep Existing Flyway Migrations

Keep these files unchanged because Flyway migrations are append-only once a database has applied them:

- `addon-expenses-rest-api/src/main/resources/db/migration/V5__create_temp_addon_expenses_time_entry_log.sql`
- `addon-expenses-rest-api/src/main/resources/db/migration/V10__create_temp_addon_expenses_log.sql`

The old tables become inert after the old JPA entity is deleted.

### 2.6 Create New Java Files

Create these files under package `com.cake.clockify.addon.mileage`:

- `src/main/java/com/cake/clockify/addon/mileage/MileageAddonApplication.java`
- `src/main/java/com/cake/clockify/addon/mileage/config/MileageDbConfig.java`
- `src/main/java/com/cake/clockify/addon/mileage/config/MileageManifestConfig.java`
- `src/main/java/com/cake/clockify/addon/mileage/config/MileageManifestV15.java`
- `src/main/java/com/cake/clockify/addon/mileage/lifecycle/MileageLifecycleHandler.java`
- `src/main/java/com/cake/clockify/addon/mileage/settings/MileageWorkspaceSettings.java`
- `src/main/java/com/cake/clockify/addon/mileage/settings/MileageSettingsRepository.java`
- `src/main/java/com/cake/clockify/addon/mileage/settings/MileageSettingsService.java`
- `src/main/java/com/cake/clockify/addon/mileage/settings/MileageSettingsValidation.java`
- `src/main/java/com/cake/clockify/addon/mileage/security/MileageAuthorizationService.java`
- `src/main/java/com/cake/clockify/addon/mileage/calculation/MileageCalculation.java`
- `src/main/java/com/cake/clockify/addon/mileage/calculation/MileageCalculator.java`
- `src/main/java/com/cake/clockify/addon/mileage/note/MileageNoteService.java`
- `src/main/java/com/cake/clockify/addon/mileage/clockify/ClockifyCategoryOption.java`
- `src/main/java/com/cake/clockify/addon/mileage/clockify/ClockifyExpenseGateway.java`
- `src/main/java/com/cake/clockify/addon/mileage/clockify/ClockifyExpenseSnapshot.java`
- `src/main/java/com/cake/clockify/addon/mileage/clockify/CreateFlatExpenseCommand.java`
- `src/main/java/com/cake/clockify/addon/mileage/clockify/UpdateFlatExpenseCommand.java`
- `src/main/java/com/cake/clockify/addon/mileage/audit/MileageConversion.java`
- `src/main/java/com/cake/clockify/addon/mileage/audit/MileageConversionRepository.java`
- `src/main/java/com/cake/clockify/addon/mileage/audit/MileageConversionSource.java`
- `src/main/java/com/cake/clockify/addon/mileage/audit/MileageConversionStatus.java`
- `src/main/java/com/cake/clockify/addon/mileage/audit/MileageSkipReason.java`
- `src/main/java/com/cake/clockify/addon/mileage/conversion/ConversionResult.java`
- `src/main/java/com/cake/clockify/addon/mileage/conversion/EligibilityDecision.java`
- `src/main/java/com/cake/clockify/addon/mileage/conversion/MileageConversionService.java`
- `src/main/java/com/cake/clockify/addon/mileage/conversion/MileageEligibilityService.java`
- `src/main/java/com/cake/clockify/addon/mileage/api/MileageApiController.java`
- `src/main/java/com/cake/clockify/addon/mileage/api/MileageSettingsController.java`
- `src/main/java/com/cake/clockify/addon/mileage/api/MileageConversionController.java`
- `src/main/java/com/cake/clockify/addon/mileage/api/MileageExceptionHandler.java`
- `src/main/java/com/cake/clockify/addon/mileage/api/model/CreateMileageExpenseRequest.java`
- `src/main/java/com/cake/clockify/addon/mileage/api/model/MileageCategoryOptionsResponse.java`
- `src/main/java/com/cake/clockify/addon/mileage/api/model/MileageConversionDetailResponse.java`
- `src/main/java/com/cake/clockify/addon/mileage/api/model/MileageConversionListResponse.java`
- `src/main/java/com/cake/clockify/addon/mileage/api/model/MileageConversionRetryResponse.java`
- `src/main/java/com/cake/clockify/addon/mileage/api/model/MileageCreateExpenseResponse.java`
- `src/main/java/com/cake/clockify/addon/mileage/api/model/MileageDiagnosticsResponse.java`
- `src/main/java/com/cake/clockify/addon/mileage/api/model/MileageErrorResponse.java`
- `src/main/java/com/cake/clockify/addon/mileage/api/model/MileagePreviewRequest.java`
- `src/main/java/com/cake/clockify/addon/mileage/api/model/MileagePreviewResponse.java`
- `src/main/java/com/cake/clockify/addon/mileage/api/model/MileageSettingsRequest.java`
- `src/main/java/com/cake/clockify/addon/mileage/api/model/MileageSettingsResponse.java`
- `src/main/java/com/cake/clockify/addon/mileage/iframe/MileageIframeController.java`
- `src/main/java/com/cake/clockify/addon/mileage/webhook/ExpenseCreatedWebhookHandler.java`
- `src/main/java/com/cake/clockify/addon/mileage/webhook/ExpenseDeletedWebhookHandler.java`
- `src/main/java/com/cake/clockify/addon/mileage/webhook/ExpenseRestoredWebhookHandler.java`
- `src/main/java/com/cake/clockify/addon/mileage/webhook/ExpenseUpdatedWebhookHandler.java`

### 2.7 Create New Resource Files

- Create `addon-expenses-rest-api/src/main/resources/db/migration/V11__create_mileage_tables.sql`.
- Create `addon-expenses-rest-api/src/main/resources/static/assets/mileage/settings.css`.
- Create `addon-expenses-rest-api/src/main/resources/static/assets/mileage/settings.js`.

### 2.8 Create New Test Files

Create these files under package `com.cake.clockify.addon.mileage`:

- `src/test/java/com/cake/clockify/addon/mileage/MileageManifestTest.java`
- `src/test/java/com/cake/clockify/addon/mileage/MileageApplicationSmokeTest.java`
- `src/test/java/com/cake/clockify/addon/mileage/MileageSecurityTest.java`
- `src/test/java/com/cake/clockify/addon/mileage/calculation/MileageCalculatorTest.java`
- `src/test/java/com/cake/clockify/addon/mileage/note/MileageNoteServiceTest.java`
- `src/test/java/com/cake/clockify/addon/mileage/settings/MileageSettingsServiceTest.java`
- `src/test/java/com/cake/clockify/addon/mileage/conversion/MileageEligibilityServiceTest.java`
- `src/test/java/com/cake/clockify/addon/mileage/conversion/MileageConversionServiceTest.java`
- `src/test/java/com/cake/clockify/addon/mileage/clockify/ClockifyExpenseGatewayTest.java`
- `src/test/java/com/cake/clockify/addon/mileage/api/MileageApiControllerTest.java`
- `src/test/java/com/cake/clockify/addon/mileage/api/MileageSettingsControllerTest.java`
- `src/test/java/com/cake/clockify/addon/mileage/api/MileageConversionControllerTest.java`
- `src/test/java/com/cake/clockify/addon/mileage/webhook/MileageWebhookIntegrationTest.java`

## 3. Database Schema

Create `src/main/resources/db/migration/V11__create_mileage_tables.sql` with exactly this schema:

```sql
CREATE TABLE IF NOT EXISTS mileage_workspace_settings (
    workspace_id                 VARCHAR(64) PRIMARY KEY,
    enabled                      BOOLEAN NOT NULL DEFAULT TRUE,
    rate                         NUMERIC(18,6),
    unit                         VARCHAR(16) NOT NULL DEFAULT 'mi',
    input_category_id            VARCHAR(64),
    output_category_id           VARCHAR(64),
    rounding_mode                VARCHAR(32) NOT NULL DEFAULT 'HALF_UP',
    convert_on_create            BOOLEAN NOT NULL DEFAULT TRUE,
    convert_on_update            BOOLEAN NOT NULL DEFAULT TRUE,
    preserve_original_notes      BOOLEAN NOT NULL DEFAULT TRUE,
    dry_run_mode                 BOOLEAN NOT NULL DEFAULT FALSE,
    allow_user_rate_override     BOOLEAN NOT NULL DEFAULT FALSE,
    note_template                TEXT,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by_user_id           VARCHAR(64),
    CONSTRAINT chk_mileage_rate_positive CHECK (rate IS NULL OR rate > 0),
    CONSTRAINT chk_mileage_rounding_mode CHECK (rounding_mode IN ('UP','DOWN','CEILING','FLOOR','HALF_UP','HALF_DOWN','HALF_EVEN'))
);

CREATE TABLE IF NOT EXISTS mileage_conversion (
    id                           UUID PRIMARY KEY,
    workspace_id                 VARCHAR(64) NOT NULL,
    expense_id                   VARCHAR(64) NOT NULL,
    source                       VARCHAR(32) NOT NULL,
    source_event_type            VARCHAR(64),
    source_category_id           VARCHAR(64),
    target_category_id           VARCHAR(64),
    user_id                      VARCHAR(64),
    project_id                   VARCHAR(64),
    task_id                      VARCHAR(64),
    miles                        NUMERIC(18,6),
    rate                         NUMERIC(18,6),
    calculated_amount            NUMERIC(18,6),
    rounded_amount               NUMERIC(18,2),
    currency                     VARCHAR(16),
    rounding_mode                VARCHAR(32),
    status                       VARCHAR(32) NOT NULL,
    skip_reason                  VARCHAR(128),
    error_code                   VARCHAR(128),
    error_message                TEXT,
    note_marker                  VARCHAR(128),
    raw_event_hash               VARCHAR(128),
    clockify_request_id          VARCHAR(128),
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    converted_at                 TIMESTAMPTZ,
    deleted_at                   TIMESTAMPTZ,
    CONSTRAINT chk_mileage_conversion_source CHECK (source IN ('ADDON_FORM','WEBHOOK_CREATED','WEBHOOK_UPDATED','WEBHOOK_RESTORED')),
    CONSTRAINT chk_mileage_conversion_status CHECK (status IN ('RECEIVED','FETCHED','DRY_RUN','SKIPPED','CONVERTING','CONVERTED','FAILED','DELETED','RESTORED_IGNORED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_mileage_conversion_workspace_expense
    ON mileage_conversion(workspace_id, expense_id);

CREATE INDEX IF NOT EXISTS ix_mileage_conversion_workspace_status
    ON mileage_conversion(workspace_id, status);

CREATE INDEX IF NOT EXISTS ix_mileage_conversion_workspace_created
    ON mileage_conversion(workspace_id, created_at DESC);
```

Do not create a separate `mileage_webhook_event_log` table in this implementation. The existing `addon-db` webhook event table already records delivery-level idempotency, and `mileage_conversion.raw_event_hash` covers conversion audit correlation.

## 4. Domain Constants

Use these exact marker and defaults:

```java
public static final String MARKER_PREFIX = "[MileageAddon:converted:v1";
public static final String DEFAULT_NOTE_TEMPLATE =
        "Mileage reimbursement: {{miles}} {{unit}} x {{rate}} = {{amount}}. Created/converted by Mileage for Clockify. {{marker}}";
public static final String DEFAULT_UNIT = "mi";
public static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_UP;
```

Use these exact enum values:

```java
public enum MileageConversionSource {
    ADDON_FORM,
    WEBHOOK_CREATED,
    WEBHOOK_UPDATED,
    WEBHOOK_RESTORED
}

public enum MileageConversionStatus {
    RECEIVED,
    FETCHED,
    DRY_RUN,
    SKIPPED,
    CONVERTING,
    CONVERTED,
    FAILED,
    DELETED,
    RESTORED_IGNORED
}

public enum MileageSkipReason {
    ADDON_DISABLED,
    SETTINGS_INCOMPLETE,
    NOT_INPUT_CATEGORY,
    ALREADY_OUTPUT_CATEGORY,
    ALREADY_MARKED,
    ALREADY_CONVERTED,
    MISSING_QUANTITY,
    INVALID_QUANTITY,
    FINALIZED_OR_LOCKED,
    DRY_RUN,
    WORKSPACE_MISMATCH,
    API_RESOURCE_NOT_FOUND
}
```

## 5. TDD Implementation Tasks

### Task 1: Maven Identity and Schema Validator Dependency

**Files:**
- Modify: `addon-expenses-rest-api/pom.xml`

- [ ] **Step 1: Write the failing command**

Run from `/Users/15x/Downloads/WORKING/addons-me/addonFactory`:

```bash
mvn -pl addon-expenses-rest-api help:evaluate -Dexpression=project.artifactId -q -DforceStdout
```

Expected failing result before implementation:

```text
clockify-expenses-api
```

- [ ] **Step 2: Modify `pom.xml`**

Set:

```xml
<artifactId>mileage-for-clockify</artifactId>
<name>Mileage for Clockify</name>
<description>Clockify add-on that creates and converts precise mileage reimbursements into real flat expenses.</description>
```

Add this test dependency inside `<dependencies>`:

```xml
<dependency>
    <groupId>com.github.java-json-tools</groupId>
    <artifactId>json-schema-validator</artifactId>
    <version>2.2.14</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 3: Run the command again**

Run:

```bash
mvn -pl addon-expenses-rest-api help:evaluate -Dexpression=project.artifactId -q -DforceStdout
```

Expected passing result:

```text
mileage-for-clockify
```

- [ ] **Step 4: Suggested commit message**

```bash
git add addon-expenses-rest-api/pom.xml
git commit -m "chore: rename mileage addon module"
```

### Task 2: Failing Manifest 1.5 Tests

**Files:**
- Create: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/MileageManifestTest.java`
- Modify later: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/config/MileageManifestConfig.java`
- Modify later: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/config/MileageManifestV15.java`

- [ ] **Step 1: Write the failing test**

Create `MileageManifestTest`:

```java
package com.cake.clockify.addon.mileage;

import com.cake.clockify.addon.db.repository.AddonWebhookTokenRepository;
import com.cake.clockify.addon.db.service.AddonInstallationService;
import com.cake.clockify.addon.db.service.AddonSettingsService;
import com.cake.clockify.addon.db.service.ClockifyClientFactory;
import com.cake.clockify.addonsdk.clockify.model.ClockifyManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                + "com.cake.clockify.addon.db.config.AddonDbAutoConfiguration",
        "addon.key=mileage-for-clockify",
        "addon.name=Mileage for Clockify",
        "addon.description=Create and convert precise mileage reimbursements into real Clockify flat expenses.",
        "addon.base-url=https://mileage.example.com",
        "addon.crypto.active-key-id=k1",
        "addon.crypto.keys.k1=00000000000000000000000000000000000000000000000000000000000000aa"
})
@AutoConfigureMockMvc
class MileageManifestTest {
    @MockBean AddonWebhookTokenRepository webhookTokenRepository;
    @MockBean AddonSettingsService addonSettingsService;
    @MockBean ClockifyClientFactory clockifyClientFactory;
    @MockBean AddonInstallationService addonInstallationService;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ClockifyManifest manifest;

    @Test
    void manifestEndpointReturnsSchema15JsonThatValidatesAgainstLocalSchema() throws Exception {
        String body = mockMvc.perform(get("/manifest"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode manifestJson = objectMapper.readTree(body);
        JsonNode schemaJson = objectMapper.readTree(new ClassPathResource("manifest-schema.json").getInputStream());
        ProcessingReport report = JsonSchemaFactory.byDefault().getJsonSchema(schemaJson).validate(manifestJson);

        assertThat(report.isSuccess()).as(report.toString()).isTrue();
        assertThat(manifestJson.path("schemaVersion").asText()).isEqualTo("1.5");
        assertThat(manifestJson.path("key").asText()).isEqualTo("mileage-for-clockify");
        assertThat(manifestJson.path("name").asText()).isEqualTo("Mileage for Clockify");
        assertThat(manifestJson.path("minimalSubscriptionPlan").asText()).isEqualTo("PRO");
        assertThat(values(manifestJson.path("scopes"))).contains("EXPENSE_READ", "EXPENSE_WRITE", "USER_READ", "PROJECT_READ", "WORKSPACE_READ");
        assertThat(webhookEvents(manifestJson)).contains("EXPENSE_CREATED", "EXPENSE_UPDATED", "EXPENSE_DELETED", "EXPENSE_RESTORED");
        assertThat(manifestJson.path("components").get(0).path("type").asText()).isEqualTo("sidebar");
        assertThat(manifestJson.path("components").get(0).path("path").asText()).isEqualTo("/iframe/mileage");
    }

    @Test
    void manifestBeanIsManualSchema15BecauseSdkHasNoV15Builder() {
        assertThat(manifest.getClass().getName()).isEqualTo("com.cake.clockify.addon.mileage.config.MileageManifestV15");
        assertThat(manifest.getSchemaVersion()).isEqualTo("1.5");
    }

    private static Set<String> values(JsonNode array) {
        Set<String> out = new HashSet<>();
        array.forEach(item -> out.add(item.asText()));
        return out;
    }

    private static Set<String> webhookEvents(JsonNode manifestJson) {
        Set<String> out = new HashSet<>();
        manifestJson.path("webhooks").forEach(item -> out.add(item.path("event").asText()));
        return out;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageManifestTest test
```

Expected failing result:

```text
Compilation failure: package com.cake.clockify.addon.mileage does not exist
```

- [ ] **Step 3: Suggested commit message**

```bash
git add addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/MileageManifestTest.java
git commit -m "test: define mileage manifest schema expectations"
```

### Task 3: Manual Schema 1.5 Manifest

**Files:**
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/MileageAddonApplication.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/config/MileageManifestConfig.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/config/MileageManifestV15.java`
- Delete after tests pass: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/ClockifyExpensesApiAddonApplication.java`
- Delete after tests pass: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/config/ClockifyExpensesApiManifestConfig.java`

- [ ] **Step 1: Implement application class**

```java
package com.cake.clockify.addon.mileage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MileageAddonApplication {
    public static void main(String[] args) {
        SpringApplication.run(MileageAddonApplication.class, args);
    }
}
```

- [ ] **Step 2: Implement manifest bean**

```java
package com.cake.clockify.addon.mileage.config;

import com.cake.clockify.addon.core.config.ClockifyAddonProperties;
import com.cake.clockify.addonsdk.clockify.model.ClockifyManifest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MileageManifestConfig {
    @Bean
    public ClockifyManifest clockifyManifest(ClockifyAddonProperties props) {
        return MileageManifestV15.from(props);
    }
}
```

- [ ] **Step 3: Implement `MileageManifestV15`**

The class must use only Java records, lists, and strings for manifest sections:

```java
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

    private MileageManifestV15(
            String key,
            String name,
            String baseUrl,
            String description) {
        this.schemaVersion = "1.5";
        this.key = key;
        this.name = name;
        this.baseUrl = baseUrl;
        this.minimalSubscriptionPlan = "PRO";
        this.description = description;
        this.iconPath = "/assets/mileage/icon.png";
        this.scopes = new ArrayList<>(List.of("EXPENSE_READ", "EXPENSE_WRITE", "USER_READ", "PROJECT_READ", "WORKSPACE_READ"));
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

    @Override public String getSchemaVersion() { return schemaVersion; }
    @Override public String getKey() { return key; }
    public String getName() { return name; }
    public String getBaseUrl() { return baseUrl; }
    public String getMinimalSubscriptionPlan() { return minimalSubscriptionPlan; }
    public String getDescription() { return description; }
    public String getIconPath() { return iconPath; }
    public List<String> getScopes() { return scopes; }
    @Override public List<Lifecycle> getLifecycle() { return lifecycle; }
    @Override public List<Webhook> getWebhooks() { return webhooks; }
    @Override public List<Component> getComponents() { return components; }
    public Object getSettings() { return settings; }
    @Override public void setSettings(Object settings) { this.settings = settings; }

    public record Lifecycle(String path, String type) {}
    public record Webhook(String event, String path) {}
    public record Component(String type, String label, String accessLevel, String path, String iconPath) {}
}
```

- [ ] **Step 4: Delete old application and manifest config**

Delete:

```text
addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/ClockifyExpensesApiAddonApplication.java
addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/config/ClockifyExpensesApiManifestConfig.java
```

- [ ] **Step 5: Run manifest test**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageManifestTest test
```

Expected passing result:

```text
Tests run: 2, Failures: 0, Errors: 0
```

- [ ] **Step 6: Suggested commit message**

```bash
git add addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/MileageManifestTest.java
git rm addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/ClockifyExpensesApiAddonApplication.java addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/config/ClockifyExpensesApiManifestConfig.java
git commit -m "feat: serve mileage manifest schema 1.5"
```

### Task 4: Database Migration, Entities, and Repositories

**Files:**
- Create: `addon-expenses-rest-api/src/main/resources/db/migration/V11__create_mileage_tables.sql`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/config/MileageDbConfig.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/settings/MileageWorkspaceSettings.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/settings/MileageSettingsRepository.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/audit/MileageConversion.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/audit/MileageConversionRepository.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/audit/MileageConversionSource.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/audit/MileageConversionStatus.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/audit/MileageSkipReason.java`
- Create: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/settings/MileageSettingsServiceTest.java`

- [ ] **Step 1: Write failing repository test**

Create `MileageSettingsServiceTest` with these test methods:

```java
@Test void settingsRepositorySavesBigDecimalRatePerWorkspace()
@Test void conversionRepositoryEnforcesWorkspaceExpenseUniqueness()
@Test void conversionQueriesAreWorkspaceIsolated()
@Test void markDeletedUpdatesStatusAndDeletedAtWithoutDeletingRow()
```

Use Testcontainers through the existing Spring Boot integration test profile. The assertions must use `BigDecimal`, `UUID`, and `Instant`, not floating point assertions.

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageSettingsServiceTest test
```

Expected failing result:

```text
Compilation failure: cannot find symbol class MileageWorkspaceSettings
```

- [ ] **Step 3: Implement migration exactly as shown in Section 3**

Copy the SQL from Section 3 into `V11__create_mileage_tables.sql`.

- [ ] **Step 4: Implement JPA config**

```java
package com.cake.clockify.addon.mileage.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ConditionalOnExpression("!'${spring.autoconfigure.exclude:}'.contains('DataSourceAutoConfiguration')")
@EnableJpaRepositories(basePackages = {
        "com.cake.clockify.addon.mileage.settings",
        "com.cake.clockify.addon.mileage.audit"
})
@EntityScan(basePackages = {
        "com.cake.clockify.addon.db.entity",
        "com.cake.clockify.addon.mileage.settings",
        "com.cake.clockify.addon.mileage.audit"
})
public class MileageDbConfig {
}
```

- [ ] **Step 5: Implement entities**

`MileageWorkspaceSettings` must use:

```java
@Id
@Column(name = "workspace_id", nullable = false, length = 64)
private String workspaceId;

@Column(name = "rate", precision = 18, scale = 6)
private BigDecimal rate;
```

`MileageConversion` must use:

```java
@Id
@Column(name = "id", nullable = false, updatable = false)
private UUID id;

@Column(name = "miles", precision = 18, scale = 6)
private BigDecimal miles;

@Column(name = "rate", precision = 18, scale = 6)
private BigDecimal rate;

@Column(name = "rounded_amount", precision = 18, scale = 2)
private BigDecimal roundedAmount;
```

Do not include `double`, `Double`, `float`, or `Float` fields in these classes.

- [ ] **Step 6: Implement repositories**

`MileageSettingsRepository`:

```java
public interface MileageSettingsRepository extends JpaRepository<MileageWorkspaceSettings, String> {
}
```

`MileageConversionRepository`:

```java
public interface MileageConversionRepository extends JpaRepository<MileageConversion, UUID> {
    Optional<MileageConversion> findByWorkspaceIdAndExpenseId(String workspaceId, String expenseId);
    Page<MileageConversion> findAllByWorkspaceId(String workspaceId, Pageable pageable);
    Page<MileageConversion> findAllByWorkspaceIdAndStatus(String workspaceId, MileageConversionStatus status, Pageable pageable);
    long countByWorkspaceIdAndExpenseId(String workspaceId, String expenseId);
}
```

- [ ] **Step 7: Run repository tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageSettingsServiceTest test
```

Expected passing result:

```text
Tests run: 4, Failures: 0, Errors: 0
```

- [ ] **Step 8: Suggested commit message**

```bash
git add addon-expenses-rest-api/src/main/resources/db/migration/V11__create_mileage_tables.sql addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/settings/MileageSettingsServiceTest.java
git commit -m "feat: add mileage settings and conversion schema"
```

### Task 5: BigDecimal Mileage Calculator

**Files:**
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/calculation/MileageCalculation.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/calculation/MileageCalculator.java`
- Create: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/calculation/MileageCalculatorTest.java`

- [ ] **Step 1: Write failing calculator tests**

Create tests with these exact method names:

```java
@Test void calculates374MilesAt0655HalfUp()
@Test void roundsOneMileAt0655HalfUpTo066()
@Test void roundsTenMilesAt0655HalfUpTo655()
@Test void rejectsZeroMiles()
@Test void rejectsNegativeMiles()
@Test void rejectsZeroRate()
@Test void supportsHalfEvenAndDown()
@Test void outputUsesPlainDecimalStrings()
```

The first test must assert:

```java
MileageCalculation result = calculator.calculate("37.4", "0.655", RoundingMode.HALF_UP);
assertThat(result.calculatedAmount()).isEqualByComparingTo(new BigDecimal("24.4970"));
assertThat(result.roundedAmount()).isEqualByComparingTo(new BigDecimal("24.50"));
assertThat(result.calculatedAmountText()).isEqualTo("24.4970");
assertThat(result.roundedAmountText()).isEqualTo("24.50");
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageCalculatorTest test
```

Expected failing result:

```text
Compilation failure: cannot find symbol class MileageCalculator
```

- [ ] **Step 3: Implement calculator records and service**

`MileageCalculation`:

```java
package com.cake.clockify.addon.mileage.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record MileageCalculation(
        BigDecimal miles,
        BigDecimal rate,
        BigDecimal calculatedAmount,
        BigDecimal roundedAmount,
        RoundingMode roundingMode
) {
    public String milesText() { return miles.stripTrailingZeros().toPlainString(); }
    public String rateText() { return rate.stripTrailingZeros().toPlainString(); }
    public String calculatedAmountText() { return calculatedAmount.toPlainString(); }
    public String roundedAmountText() { return roundedAmount.setScale(2, roundingMode).toPlainString(); }
}
```

`MileageCalculator`:

```java
package com.cake.clockify.addon.mileage.calculation;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class MileageCalculator {
    public MileageCalculation calculate(String milesText, String rateText, RoundingMode roundingMode) {
        BigDecimal miles = parsePositive("miles", milesText);
        BigDecimal rate = parsePositive("rate", rateText);
        RoundingMode mode = roundingMode == null ? RoundingMode.HALF_UP : roundingMode;
        BigDecimal calculated = miles.multiply(rate);
        BigDecimal rounded = calculated.setScale(2, mode);
        return new MileageCalculation(miles, rate, calculated, rounded, mode);
    }

    private static BigDecimal parsePositive(String field, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(field + " must be greater than zero");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a decimal number", e);
        }
    }
}
```

- [ ] **Step 4: Run calculator tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageCalculatorTest test
```

Expected passing result:

```text
Tests run: 8, Failures: 0, Errors: 0
```

- [ ] **Step 5: Suggested commit message**

```bash
git add addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/calculation addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/calculation/MileageCalculatorTest.java
git commit -m "feat: add BigDecimal mileage calculator"
```

### Task 6: Mileage Settings Service and Admin Authorization

**Files:**
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/settings/MileageSettingsService.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/settings/MileageSettingsValidation.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/security/MileageAuthorizationService.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/model/MileageSettingsRequest.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/model/MileageSettingsResponse.java`
- Extend: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/settings/MileageSettingsServiceTest.java`

- [ ] **Step 1: Add failing tests**

Add these methods to `MileageSettingsServiceTest`:

```java
@Test void returnsDefaultIncompleteSettingsWhenWorkspaceHasNoRow()
@Test void savesSettingsWithBigDecimalRate()
@Test void rejectsInvalidRoundingMode()
@Test void validationRequiresRateAndOutputCategoryForCreateExpense()
@Test void adminAuthorizationAllowsOwnerAndAdmin()
@Test void adminAuthorizationRejectsMember()
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageSettingsServiceTest test
```

Expected failing result:

```text
Compilation failure: cannot find symbol class MileageSettingsService
```

- [ ] **Step 3: Implement DTOs with decimal strings**

`MileageSettingsRequest` fields:

```java
Boolean enabled;
String rate;
String unit;
String inputCategoryId;
String outputCategoryId;
String roundingMode;
Boolean convertOnCreate;
Boolean convertOnUpdate;
Boolean preserveOriginalNotes;
Boolean dryRunMode;
Boolean allowUserRateOverride;
String noteTemplate;
```

`MileageSettingsResponse` fields:

```java
boolean enabled;
String rate;
String unit;
String inputCategoryId;
String outputCategoryId;
String roundingMode;
boolean convertOnCreate;
boolean convertOnUpdate;
boolean preserveOriginalNotes;
boolean dryRunMode;
boolean allowUserRateOverride;
String noteTemplate;
boolean completeForAddonCreate;
boolean completeForNativeConversion;
List<String> diagnostics;
```

- [ ] **Step 4: Implement service methods**

`MileageSettingsService` must expose:

```java
public MileageSettingsResponse getEffectiveSettings(String workspaceId)
public MileageWorkspaceSettings saveSettings(String workspaceId, MileageSettingsRequest request, String updatedByUserId)
public MileageSettingsValidation validateForAddonCreate(String workspaceId)
public MileageSettingsValidation validateForNativeConversion(String workspaceId)
```

Rules:

- If no row exists, return enabled `true`, unit `"mi"`, rounding `"HALF_UP"`, conversion toggles `true`, dry-run `false`, and diagnostics for missing `rate` and `outputCategoryId`.
- Native conversion additionally requires `inputCategoryId`.
- Store `rate` as `BigDecimal`.
- Reject invalid rounding mode by throwing `IllegalArgumentException("roundingMode must be a Java RoundingMode name")`.

- [ ] **Step 5: Implement admin authorization**

`MileageAuthorizationService`:

```java
package com.cake.clockify.addon.mileage.security;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Service
public class MileageAuthorizationService {
    private static final Set<String> ADMIN_ROLES = Set.of("OWNER", "ADMIN");

    public void requireAdmin(NormalizedClaims claims) {
        String role = claims == null ? null : claims.workspaceRole();
        if (role == null || !ADMIN_ROLES.contains(role.toUpperCase(java.util.Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role is required");
        }
    }
}
```

- [ ] **Step 6: Run settings tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageSettingsServiceTest test
```

Expected passing result:

```text
Tests run: 10, Failures: 0, Errors: 0
```

- [ ] **Step 7: Suggested commit message**

```bash
git add addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/settings addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/security addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/model addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/settings/MileageSettingsServiceTest.java
git commit -m "feat: add mileage workspace settings service"
```

### Task 7: Clockify Expense Gateway Wrapper

**Files:**
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/clockify/ClockifyExpenseGateway.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/clockify/ClockifyExpenseSnapshot.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/clockify/ClockifyCategoryOption.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/clockify/CreateFlatExpenseCommand.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/clockify/UpdateFlatExpenseCommand.java`
- Create: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/clockify/ClockifyExpenseGatewayTest.java`

- [ ] **Step 1: Write failing gateway tests**

Create tests:

```java
@Test void fetchExpenseMapsQuantityAndTotalWithBigDecimal()
@Test void createFlatExpenseSendsOutputCategoryAndAmountAsPlainString()
@Test void createFlatExpenseWithReceiptForwardsFileOnlyToClockify()
@Test void updateFlatExpenseSendsCategoryAmountNotesAndChangeFields()
@Test void listCategoriesNormalizesUnitAndFlatTypes()
@Test void gatewayUsesClockifyClientFactoryForWorkspace()
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=ClockifyExpenseGatewayTest test
```

Expected failing result:

```text
Compilation failure: cannot find symbol class ClockifyExpenseGateway
```

- [ ] **Step 3: Implement snapshot and commands**

`ClockifyExpenseSnapshot` must be a record:

```java
public record ClockifyExpenseSnapshot(
        String id,
        String workspaceId,
        String userId,
        String date,
        String projectId,
        String taskId,
        String categoryId,
        String notes,
        BigDecimal quantity,
        Boolean billable,
        String fileId,
        BigDecimal total,
        Boolean locked
) {}
```

`CreateFlatExpenseCommand` and `UpdateFlatExpenseCommand` must store `BigDecimal amount`.

- [ ] **Step 4: Implement gateway**

Required public methods:

```java
public ClockifyExpenseSnapshot getExpense(String workspaceId, String expenseId)
public JsonNode createFlatExpense(String workspaceId, CreateFlatExpenseCommand command)
public JsonNode createFlatExpenseWithReceipt(String workspaceId, CreateFlatExpenseCommand command, String fileName, String contentType, byte[] fileBytes)
public JsonNode updateFlatExpense(String workspaceId, String expenseId, UpdateFlatExpenseCommand command)
public List<ClockifyCategoryOption> listCategories(String workspaceId)
```

Implementation guidance:

- Call `clientFactory.getClient(workspaceId)` inside each method.
- Do not build or hardcode a Clockify host string.
- Convert numeric JSON values to `BigDecimal` with `node.decimalValue()` if `node.isNumber()`.
- Convert numeric strings to `BigDecimal` with `new BigDecimal(node.asText())`.
- Serialize `amount` with `amount.setScale(2, roundingMode).toPlainString()`.
- For conversion update, send `categoryId`, `amount`, `notes`, and `changeFields` value `"CATEGORY,AMOUNT,NOTES"`.
- Do not include installation token, user token, request headers, or receipt bytes in returned DTOs.

- [ ] **Step 5: Run gateway tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=ClockifyExpenseGatewayTest test
```

Expected passing result:

```text
Tests run: 6, Failures: 0, Errors: 0
```

- [ ] **Step 6: Suggested commit message**

```bash
git add addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/clockify addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/clockify/ClockifyExpenseGatewayTest.java
git commit -m "feat: wrap Clockify expense operations for mileage"
```

### Task 8: Mileage Note Marker Service

**Files:**
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/note/MileageNoteService.java`
- Create: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/note/MileageNoteServiceTest.java`

- [ ] **Step 1: Write failing note tests**

Create tests:

```java
@Test void buildsMarkerFromConversionId()
@Test void appendsHumanFormulaAndMarkerToBlankNote()
@Test void preservesOriginalNoteWhenConfigured()
@Test void replacesOriginalNoteWhenPreserveFalse()
@Test void doesNotDuplicateMarker()
@Test void detectsExistingMarker()
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageNoteServiceTest test
```

Expected failing result:

```text
Compilation failure: cannot find symbol class MileageNoteService
```

- [ ] **Step 3: Implement note service signatures**

```java
public String marker(UUID conversionId)
public boolean hasMileageMarker(String notes)
public String buildConvertedNote(String originalNote, MileageCalculation calculation, String unit, UUID conversionId, boolean preserveOriginalNotes, String template)
```

Required behavior:

- Marker format: `[MileageAddon:converted:v1 id={uuid}]`.
- If `preserveOriginalNotes` is `true`, put original note first, then one blank line, then generated mileage formula.
- If original note already contains `MARKER_PREFIX`, return original note unchanged.
- Use `MileageCalculation` text methods so decimal formatting stays plain.

- [ ] **Step 4: Run note tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageNoteServiceTest test
```

Expected passing result:

```text
Tests run: 6, Failures: 0, Errors: 0
```

- [ ] **Step 5: Suggested commit message**

```bash
git add addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/note addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/note/MileageNoteServiceTest.java
git commit -m "feat: add mileage conversion note markers"
```

### Task 9: Eligibility Service and Loop Prevention

**Files:**
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/conversion/EligibilityDecision.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/conversion/MileageEligibilityService.java`
- Create: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/conversion/MileageEligibilityServiceTest.java`

- [ ] **Step 1: Write failing eligibility tests**

Create tests:

```java
@Test void disabledSettingsAreSkipped()
@Test void missingOutputCategoryIsSkipped()
@Test void nonInputCategoryIsSkipped()
@Test void outputCategoryIsSkippedAsAlreadyOutputCategory()
@Test void markerIsSkippedAsAlreadyMarked()
@Test void existingConvertedRowIsSkipped()
@Test void missingQuantityIsSkipped()
@Test void negativeQuantityIsSkipped()
@Test void lockedExpenseIsSkipped()
@Test void dryRunReturnsDryRunDecision()
@Test void eligibleInputExpenseReturnsEligible()
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageEligibilityServiceTest test
```

Expected failing result:

```text
Compilation failure: cannot find symbol class MileageEligibilityService
```

- [ ] **Step 3: Implement decision record**

```java
public record EligibilityDecision(
        boolean eligible,
        boolean dryRun,
        MileageSkipReason skipReason,
        String message
) {
    public static EligibilityDecision eligible() { return new EligibilityDecision(true, false, null, "Eligible"); }
    public static EligibilityDecision dryRun() { return new EligibilityDecision(false, true, MileageSkipReason.DRY_RUN, "Dry-run mode enabled"); }
    public static EligibilityDecision skipped(MileageSkipReason reason, String message) { return new EligibilityDecision(false, false, reason, message); }
}
```

- [ ] **Step 4: Implement eligibility method**

```java
public EligibilityDecision evaluate(
        MileageSettingsValidation settings,
        ClockifyExpenseSnapshot expense,
        boolean successfulConversionExists)
```

Rules must run in this exact order:

1. If settings says disabled, return `ADDON_DISABLED`.
2. If settings is incomplete, return `SETTINGS_INCOMPLETE`.
3. If expense workspace ID exists and differs from settings workspace ID, return `WORKSPACE_MISMATCH`.
4. If category equals output category, return `ALREADY_OUTPUT_CATEGORY`.
5. If note has marker, return `ALREADY_MARKED`.
6. If successful conversion exists, return `ALREADY_CONVERTED`.
7. If category differs from input category, return `NOT_INPUT_CATEGORY`.
8. If quantity is null, return `MISSING_QUANTITY`.
9. If quantity is less than or equal to zero, return `INVALID_QUANTITY`.
10. If locked is true, return `FINALIZED_OR_LOCKED`.
11. If dry-run mode is true, return dry-run decision.
12. Return eligible.

- [ ] **Step 5: Run eligibility tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageEligibilityServiceTest test
```

Expected passing result:

```text
Tests run: 11, Failures: 0, Errors: 0
```

- [ ] **Step 6: Suggested commit message**

```bash
git add addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/conversion addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/conversion/MileageEligibilityServiceTest.java
git commit -m "feat: add mileage conversion eligibility guards"
```

### Task 10: Add-on Created Mileage Expense API

**Files:**
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/MileageApiController.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/MileageExceptionHandler.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/model/CreateMileageExpenseRequest.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/model/MileageCreateExpenseResponse.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/model/MileagePreviewRequest.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/model/MileagePreviewResponse.java`
- Create: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/api/MileageApiControllerTest.java`

- [ ] **Step 1: Write failing API tests**

Create tests:

```java
@Test void previewReturnsCalculatedAndRoundedAmounts()
@Test void createMileageExpenseJsonCreatesFlatClockifyExpense()
@Test void createMileageExpenseMultipartForwardsReceipt()
@Test void createMileageExpenseUsesConfiguredOutputCategory()
@Test void createMileageExpenseCreatesAddonFormAuditRow()
@Test void createMileageExpenseRejectsMissingSettings()
@Test void createMileageExpenseRejectsUserRateOverrideWhenDisabled()
@Test void validationErrorDoesNotLeakStackTrace()
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageApiControllerTest test
```

Expected failing result:

```text
Compilation failure: cannot find symbol class MileageApiController
```

- [ ] **Step 3: Implement DTO records**

Use string fields for decimals:

```java
public record MileagePreviewRequest(@NotBlank String miles, String rate) {}

public record MileagePreviewResponse(
        String miles,
        String rate,
        String calculatedAmount,
        String roundedAmount,
        String roundingMode
) {}

public record CreateMileageExpenseRequest(
        @NotNull LocalDate date,
        @NotBlank @Size(max = 64) String userId,
        @Size(max = 64) String projectId,
        @Size(max = 64) String taskId,
        @NotBlank String miles,
        String rate,
        Boolean billable,
        @Size(max = 8000) String notes
) {}

public record MileageCreateExpenseResponse(
        UUID conversionId,
        String expenseId,
        String status,
        String miles,
        String rate,
        String roundedAmount,
        String noteMarker
) {}
```

- [ ] **Step 4: Implement controller routes**

`MileageApiController` routes:

```java
@PostMapping("/api/mileage/preview")
public MileagePreviewResponse preview(HttpServletRequest request, @Valid @RequestBody MileagePreviewRequest body)

@PostMapping(value = "/api/mileage/expenses", consumes = MediaType.APPLICATION_JSON_VALUE)
public MileageCreateExpenseResponse createJson(HttpServletRequest request, @Valid @RequestBody CreateMileageExpenseRequest body)

@PostMapping(value = "/api/mileage/expenses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public MileageCreateExpenseResponse createMultipart(HttpServletRequest request, @RequestParam Map<String, String> params, @RequestParam(value = "file", required = false) MultipartFile file)
```

Implementation rules:

- Use `RequestAttributes.requireClaims(request)` for workspace and user context.
- Load settings through `MileageSettingsService.validateForAddonCreate`.
- If `allowUserRateOverride` is false, ignore request rate and use admin setting rate.
- If `allowUserRateOverride` is true, require request rate when supplied and validate it with `MileageCalculator`.
- Use `outputCategoryId` from settings.
- Create a conversion ID before note generation.
- Create Clockify expense with `CreateFlatExpenseCommand`.
- Save `MileageConversion` with source `ADDON_FORM` and status `CONVERTED` after Clockify returns expense ID.
- For multipart, whitelist only `date`, `userId`, `projectId`, `taskId`, `miles`, `rate`, `billable`, and `notes`.
- Do not forward `auth_token`, `Authorization`, `addonToken`, or any unknown form field.

- [ ] **Step 5: Implement exception handler**

Return sanitized errors:

```json
{"error":"configuration_missing","message":"Mileage output category is not configured"}
```

Never return exception class names, stack traces, raw upstream bodies, tokens, or file bytes.

- [ ] **Step 6: Run API tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageApiControllerTest test
```

Expected passing result:

```text
Tests run: 8, Failures: 0, Errors: 0
```

- [ ] **Step 7: Suggested commit message**

```bash
git add addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/api/MileageApiControllerTest.java
git commit -m "feat: add mileage expense creation API"
```

### Task 11: Mileage Conversion Service for Native Webhooks

**Files:**
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/conversion/ConversionResult.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/conversion/MileageConversionService.java`
- Create: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/conversion/MileageConversionServiceTest.java`

- [ ] **Step 1: Write failing conversion tests**

Create tests:

```java
@Test void eligibleCreatedWebhookConvertsExpenseOnce()
@Test void conversionUsesFetchedQuantityAsMiles()
@Test void conversionUpdatesSameExpenseToOutputCategory()
@Test void conversionAppendsMarker()
@Test void dryRunCreatesDryRunAuditAndDoesNotUpdateClockify()
@Test void existingConvertedAuditPreventsSecondUpdate()
@Test void outputCategoryPreventsSecondUpdate()
@Test void markerPreventsSecondUpdate()
@Test void clockifyConflictRecordsFailedSanitized()
@Test void crossWorkspaceFetchedExpenseIsSkipped()
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageConversionServiceTest test
```

Expected failing result:

```text
Compilation failure: cannot find symbol class MileageConversionService
```

- [ ] **Step 3: Implement result record**

```java
public record ConversionResult(
        UUID conversionId,
        String expenseId,
        MileageConversionStatus status,
        MileageSkipReason skipReason,
        String message
) {}
```

- [ ] **Step 4: Implement service methods**

Required public methods:

```java
@Transactional
public ConversionResult convertIfEligible(NormalizedClaims claims, String expenseId, MileageConversionSource source, String sourceEventType)

@Transactional
public ConversionResult markDeleted(NormalizedClaims claims, String expenseId)

@Transactional
public ConversionResult retry(NormalizedClaims claims, UUID conversionId)
```

Implementation sequence inside `convertIfEligible`:

1. Reject blank `expenseId` with `SKIPPED` and `API_RESOURCE_NOT_FOUND`.
2. Load settings validation for native conversion.
3. Fetch full expense through `ClockifyExpenseGateway.getExpense`.
4. Check fetched workspace ID against `claims.workspaceId()` when fetched workspace ID is not blank.
5. Look up existing conversion by `(workspaceId, expenseId)`.
6. Evaluate eligibility with `MileageEligibilityService`.
7. If dry-run, save conversion status `DRY_RUN` and return.
8. If skipped, save or update conversion status `SKIPPED` with exact skip reason and return.
9. Generate conversion ID.
10. Calculate using fetched `quantity` as miles and settings `rate`.
11. Build note with `MileageNoteService`.
12. Save conversion status `CONVERTING`.
13. Call `ClockifyExpenseGateway.updateFlatExpense`.
14. Save conversion status `CONVERTED`, `convertedAt`, `miles`, `rate`, `calculatedAmount`, `roundedAmount`, `sourceCategoryId`, `targetCategoryId`, and `noteMarker`.
15. Return `ConversionResult` with status `CONVERTED`.

On `ClockifyApiException`:

- Save status `FAILED`.
- Save `errorCode` as `clockify_api_error`.
- Save `errorMessage` as a sanitized message with status code only.
- Do not save tokens or raw response bodies.

On `InterruptedException`:

- Call `Thread.currentThread().interrupt()`.
- Save status `FAILED`.
- Save `errorCode` as `interrupted`.

- [ ] **Step 5: Run conversion tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageConversionServiceTest test
```

Expected passing result:

```text
Tests run: 10, Failures: 0, Errors: 0
```

- [ ] **Step 6: Suggested commit message**

```bash
git add addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/conversion addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/conversion/MileageConversionServiceTest.java
git commit -m "feat: convert native mileage expenses safely"
```

### Task 12: Webhook Handlers

**Files:**
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/webhook/ExpenseCreatedWebhookHandler.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/webhook/ExpenseUpdatedWebhookHandler.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/webhook/ExpenseDeletedWebhookHandler.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/webhook/ExpenseRestoredWebhookHandler.java`
- Create: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/webhook/MileageWebhookIntegrationTest.java`
- Delete after replacement: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/webhook/ClockifyExpensesApiExpenseLog.java`
- Delete after replacement: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/webhook/ClockifyExpensesApiExpenseLogRepository.java`
- Delete after replacement: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/webhook/ExpenseCreatedHandler.java`
- Delete after replacement: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/webhook/ExpenseDeletedHandler.java`
- Delete after replacement: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/webhook/ExpenseLogService.java`
- Delete after replacement: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/webhook/ExpenseRestoredHandler.java`
- Delete after replacement: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/webhook/ExpenseUpdatedHandler.java`

- [ ] **Step 1: Write failing webhook integration tests**

Create tests:

```java
@Test void expenseCreatedWebhookFetchesAndConverts()
@Test void expenseUpdatedWebhookAfterConversionIsIgnored()
@Test void expenseUpdatedWebhookCanRepairUnconvertedInputExpense()
@Test void expenseDeletedMarksAuditDeleted()
@Test void expenseRestoredAlreadyConvertedIsIgnored()
@Test void expenseRestoredInputCategoryConverts()
@Test void duplicateWebhookDeliveryDoesNotCreateSecondConversion()
@Test void invalidWebhookSignatureIsUnauthorized()
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageWebhookIntegrationTest test
```

Expected failing result:

```text
Compilation failure: cannot find symbol class ExpenseCreatedWebhookHandler
```

- [ ] **Step 3: Implement handlers**

`ExpenseCreatedWebhookHandler`:

```java
@Component
@WebhookEvent("EXPENSE_CREATED")
public class ExpenseCreatedWebhookHandler extends AbstractTypedWebhookHandler<ExpenseWebhookPayload> {
    private final MileageConversionService conversionService;
    public ExpenseCreatedWebhookHandler(ObjectMapper objectMapper, MileageConversionService conversionService) {
        super(objectMapper, ExpenseWebhookPayload.class);
        this.conversionService = conversionService;
    }
    @Override protected void handleTyped(NormalizedClaims claims, String eventType, ExpenseWebhookPayload payload) {
        if (payload != null && payload.id() != null && !payload.id().isBlank()) {
            conversionService.convertIfEligible(claims, payload.id(), MileageConversionSource.WEBHOOK_CREATED, eventType);
        }
    }
}
```

`ExpenseUpdatedWebhookHandler` must parse `ExpenseRefWebhookPayload` and call source `WEBHOOK_UPDATED`.

`ExpenseDeletedWebhookHandler` must parse `ExpenseRefWebhookPayload` and call `markDeleted`.

`ExpenseRestoredWebhookHandler` must parse `ExpenseWebhookPayload` and call source `WEBHOOK_RESTORED`.

All handlers must return without throwing when payload is null or expense ID is blank. The auth filter and core webhook controller handle signature verification and event-type matching before these handlers run.

- [ ] **Step 4: Delete old generic webhook files**

Delete:

```text
src/main/java/com/cake/clockify/addon/expenses/webhook/ClockifyExpensesApiExpenseLog.java
src/main/java/com/cake/clockify/addon/expenses/webhook/ClockifyExpensesApiExpenseLogRepository.java
src/main/java/com/cake/clockify/addon/expenses/webhook/ExpenseCreatedHandler.java
src/main/java/com/cake/clockify/addon/expenses/webhook/ExpenseDeletedHandler.java
src/main/java/com/cake/clockify/addon/expenses/webhook/ExpenseLogService.java
src/main/java/com/cake/clockify/addon/expenses/webhook/ExpenseRestoredHandler.java
src/main/java/com/cake/clockify/addon/expenses/webhook/ExpenseUpdatedHandler.java
```

- [ ] **Step 5: Run webhook integration tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageWebhookIntegrationTest test
```

Expected passing result:

```text
Tests run: 8, Failures: 0, Errors: 0
```

- [ ] **Step 6: Suggested commit message**

```bash
git add addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/webhook addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/webhook/MileageWebhookIntegrationTest.java
git rm addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/webhook/*.java
git commit -m "feat: wire mileage expense webhook conversion"
```

### Task 13: Delete and Restore Audit Behavior

**Files:**
- Modify: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/conversion/MileageConversionService.java`
- Modify: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/conversion/MileageConversionServiceTest.java`
- Modify: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/webhook/MileageWebhookIntegrationTest.java`

- [ ] **Step 1: Add failing delete/restore tests**

Add:

```java
@Test void markDeletedDoesNotHardDeleteConversion()
@Test void restoredAlreadyConvertedExpenseReturnsRestoredIgnored()
@Test void restoredInputCategoryExpenseConvertsWhenNoSuccessfulConversionExists()
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageConversionServiceTest,MileageWebhookIntegrationTest test
```

Expected failing result:

```text
expected status DELETED but was CONVERTED
```

- [ ] **Step 3: Implement status transitions**

`markDeleted`:

- Find conversion by `(workspaceId, expenseId)`.
- If found, set status `DELETED`, set `deletedAt`, update `updatedAt`, and save.
- If not found, return `ConversionResult` with status `SKIPPED` and skip reason `API_RESOURCE_NOT_FOUND`.
- Never call `repository.delete`.

Restore behavior:

- If expense has marker or existing conversion status `CONVERTED`, save or return status `RESTORED_IGNORED`.
- If restored expense is in input category and no converted row exists, run full conversion path.

- [ ] **Step 4: Run tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageConversionServiceTest,MileageWebhookIntegrationTest test
```

Expected passing result:

```text
Failures: 0, Errors: 0
```

- [ ] **Step 5: Suggested commit message**

```bash
git add addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/conversion/MileageConversionService.java addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/conversion/MileageConversionServiceTest.java addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/webhook/MileageWebhookIntegrationTest.java
git commit -m "feat: preserve mileage conversion audit on delete and restore"
```

### Task 14: Settings, Category Options, Diagnostics, and Conversion Admin APIs

**Files:**
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/MileageSettingsController.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/MileageConversionController.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/model/MileageCategoryOptionsResponse.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/model/MileageConversionDetailResponse.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/model/MileageConversionListResponse.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/model/MileageConversionRetryResponse.java`
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/model/MileageDiagnosticsResponse.java`
- Create: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/api/MileageSettingsControllerTest.java`
- Create: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/api/MileageConversionControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

`MileageSettingsControllerTest`:

```java
@Test void adminCanReadSettings()
@Test void adminCanSaveSettings()
@Test void memberCannotSaveSettings()
@Test void adminCanListCategoryOptions()
@Test void diagnosticsReportsMissingSettings()
```

`MileageConversionControllerTest`:

```java
@Test void adminCanListConversions()
@Test void adminCanReadConversionDetail()
@Test void adminCanRetryFailedConversion()
@Test void memberCannotReadConversionLog()
@Test void conversionListIsWorkspaceIsolated()
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageSettingsControllerTest,MileageConversionControllerTest test
```

Expected failing result:

```text
Compilation failure: cannot find symbol class MileageSettingsController
```

- [ ] **Step 3: Implement routes**

`MileageSettingsController`:

```java
@GetMapping("/api/mileage/settings")
@PutMapping("/api/mileage/settings")
@GetMapping("/api/mileage/options/categories")
@GetMapping("/api/mileage/diagnostics")
```

`MileageConversionController`:

```java
@GetMapping("/api/mileage/conversions")
@GetMapping("/api/mileage/conversions/{id}")
@PostMapping("/api/mileage/conversions/{id}/retry")
```

Rules:

- Every route must call `RequestAttributes.requireClaims(request)`.
- Every admin route must call `MileageAuthorizationService.requireAdmin(claims)`.
- Every repository query must filter by `claims.workspaceId()`.
- `pageSize` must be clamped to minimum `1` and maximum `100`.
- `GET /api/mileage/options/categories` must return category type `"UNIT"` when `hasUnitPrice` is true and `"FLAT"` when false.
- `GET /api/mileage/diagnostics` must report `installationAvailable`, `settingsComplete`, `nativeConversionReady`, and `warnings`.

- [ ] **Step 4: Run controller tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageSettingsControllerTest,MileageConversionControllerTest test
```

Expected passing result:

```text
Tests run: 10, Failures: 0, Errors: 0
```

- [ ] **Step 5: Suggested commit message**

```bash
git add addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/api
git commit -m "feat: add mileage admin settings and audit APIs"
```

### Task 15: Iframe UI Replacement

**Files:**
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/iframe/MileageIframeController.java`
- Create: `addon-expenses-rest-api/src/main/resources/static/assets/mileage/settings.css`
- Create: `addon-expenses-rest-api/src/main/resources/static/assets/mileage/settings.js`
- Delete: `addon-expenses-rest-api/src/main/resources/static/assets/clockify-expenses-api/settings.css`
- Delete: `addon-expenses-rest-api/src/main/resources/static/assets/clockify-expenses-api/settings.js`
- Delete after replacement: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/iframe/SettingsIframeController.java`
- Extend: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/MileageSecurityTest.java`

- [ ] **Step 1: Write failing iframe tests**

Create or extend `MileageSecurityTest`:

```java
@Test void mileageIframeRequiresAuthToken()
@Test void mileageIframeUsesExternalCssAndJs()
@Test void mileageIframeDoesNotContainInlineScriptOrInlineStyle()
@Test void mileageJavascriptRemovesAuthTokenFromLocation()
@Test void mileageJavascriptUsesAuthorizationHeaderForBackendCalls()
@Test void nonAdminUserDoesNotSeeAdminControlsAfterClaimsLoad()
```

- [ ] **Step 2: Run test to verify failure**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageSecurityTest test
```

Expected failing result:

```text
Compilation failure: cannot find symbol class MileageIframeController
```

- [ ] **Step 3: Implement iframe routes**

`MileageIframeController`:

```java
@GetMapping(value = "/iframe/mileage", produces = MediaType.TEXT_HTML_VALUE)
public ResponseEntity<String> mileage()

@GetMapping(value = "/iframe/settings", produces = MediaType.TEXT_HTML_VALUE)
public ResponseEntity<String> settings()
```

Both methods can return the same HTML shell. The HTML must:

- Link `/assets/mileage/settings.css`.
- Link `/assets/mileage/settings.js` with `defer`.
- Contain tabs with IDs `tab-create`, `tab-mine`, `tab-admin-settings`, `tab-conversion-log`, and `tab-diagnostics`.
- Not contain inline `<script>`, inline `<style>`, or inline event handlers.

- [ ] **Step 4: Implement JavaScript**

`settings.js` must:

- Read `auth_token` from query string.
- Store it only in a local closure variable.
- Remove `auth_token` from browser location with `history.replaceState`.
- Add `Authorization: Bearer {authToken}` to backend requests.
- Decode token payload only for UI role gating.
- Hide admin tabs unless `workspaceRole` is `OWNER` or `ADMIN`.
- Call `/api/mileage/preview` for preview.
- Call `/api/mileage/expenses` for JSON submit and multipart receipt submit.
- Call `/api/mileage/settings`, `/api/mileage/options/categories`, `/api/mileage/conversions`, and `/api/mileage/diagnostics` only from admin UI.
- Display errors using sanitized `message` from backend and never display raw exception text.

- [ ] **Step 5: Implement CSS**

Use a restrained operational UI with:

- Left navigation.
- Dense forms.
- Tables for conversions.
- No nested cards.
- No gradient orb backgrounds.
- Mobile single-column layout below `760px`.

- [ ] **Step 6: Delete old generic iframe and assets**

Delete:

```text
src/main/java/com/cake/clockify/addon/expenses/iframe/SettingsIframeController.java
src/main/resources/static/assets/clockify-expenses-api/settings.css
src/main/resources/static/assets/clockify-expenses-api/settings.js
```

- [ ] **Step 7: Run iframe/security tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageSecurityTest test
```

Expected passing result:

```text
Tests run: 6, Failures: 0, Errors: 0
```

- [ ] **Step 8: Suggested commit message**

```bash
git add addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/iframe addon-expenses-rest-api/src/main/resources/static/assets/mileage addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/MileageSecurityTest.java
git rm addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/iframe/SettingsIframeController.java addon-expenses-rest-api/src/main/resources/static/assets/clockify-expenses-api/settings.css addon-expenses-rest-api/src/main/resources/static/assets/clockify-expenses-api/settings.js
git commit -m "feat: replace iframe UI with mileage workflow"
```

### Task 16: Lifecycle Handler and Workspace Cleanup

**Files:**
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/lifecycle/MileageLifecycleHandler.java`
- Delete after replacement: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/lifecycle/ClockifyExpensesApiLifecycleHandler.java`
- Extend: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/MileageApplicationSmokeTest.java`

- [ ] **Step 1: Write failing lifecycle tests**

Create tests:

```java
@Test void installedLifecycleLeavesTokenPersistenceToAddonDb()
@Test void deletedLifecycleDeletesMileageSettingsAndConversionsForWorkspace()
@Test void settingsUpdatedLifecycleSyncsKnownMileageSettings()
@Test void statusChangedInactiveDisablesExistingSettingsRow()
@Test void statusChangedActiveEnablesExistingSettingsRow()
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageApplicationSmokeTest test
```

Expected failing result:

```text
Compilation failure: cannot find symbol class MileageLifecycleHandler
```

- [ ] **Step 3: Implement lifecycle handler**

Rules:

- `onInstalled` logs only workspace ID and never logs payload tokens.
- `onDeleted` deletes `mileage_workspace_settings` by workspace ID and deletes `mileage_conversion` rows by workspace ID. This is workspace uninstall cleanup, not expense delete behavior.
- `onSettingsUpdated` maps known setting IDs from Clockify structured settings into `MileageSettingsService.saveSettings`.
- `onStatusChanged` updates `enabled` only when a settings row already exists; it must not create a row with missing rate/category values.
- Do not persist lifecycle token; `JpaPersistenceLifecycleHandler` already does that.

- [ ] **Step 4: Delete old lifecycle handler**

Delete:

```text
src/main/java/com/cake/clockify/addon/expenses/lifecycle/ClockifyExpensesApiLifecycleHandler.java
```

- [ ] **Step 5: Run lifecycle tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageApplicationSmokeTest test
```

Expected passing result:

```text
Tests run: 5, Failures: 0, Errors: 0
```

- [ ] **Step 6: Suggested commit message**

```bash
git add addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/lifecycle addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/MileageApplicationSmokeTest.java
git rm addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/lifecycle/ClockifyExpensesApiLifecycleHandler.java
git commit -m "feat: add mileage lifecycle cleanup"
```

### Task 17: Product Rename Configuration and Docker

**Files:**
- Modify: `addon-expenses-rest-api/src/main/resources/application.yaml`
- Modify: `addon-expenses-rest-api/src/test/resources/application-test.yaml`
- Modify: `addon-expenses-rest-api/Dockerfile`
- Modify: `addon-expenses-rest-api/docker-compose.yml`
- Modify: `addon-expenses-rest-api/README.md`
- Delete or replace docs: `addon-expenses-rest-api/endpoints.md`, `addon-expenses-rest-api/models.md`, `addon-expenses-rest-api/reports.md`, `addon-expenses-rest-api/webhooks.md`, `addon-expenses-rest-api/edge-cases.md`

- [ ] **Step 1: Write failing smoke assertions**

Add to `MileageApplicationSmokeTest`:

```java
@Test void applicationPropertiesUseMileageDefaults()
@Test void readmeDoesNotAdvertiseGenericExpensesApiExplorer()
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageApplicationSmokeTest test
```

Expected failing result:

```text
expected addon key mileage-for-clockify but was clockify-expenses-api
```

- [ ] **Step 3: Update configuration**

`application.yaml` values:

```yaml
spring:
  application:
    name: mileage-for-clockify
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/mileage_for_clockify}

addon:
  key: ${ADDON_KEY:mileage-for-clockify}
  name: ${ADDON_NAME:Mileage for Clockify}
  description: ${ADDON_DESCRIPTION:Create and convert precise mileage reimbursements into real Clockify flat expenses.}
```

`application-test.yaml` must use the same addon key/name/description and keep test crypto key.

- [ ] **Step 4: Update Docker files**

`Dockerfile`:

```dockerfile
COPY --from=builder /build/addon-expenses-rest-api/target/mileage-for-clockify-*.jar app.jar
```

`docker-compose.yml`:

```yaml
container_name: mileage-for-clockify-db
POSTGRES_DB: mileage_for_clockify
SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/mileage_for_clockify
ADDON_KEY: mileage-for-clockify
ADDON_NAME: Mileage for Clockify
ADDON_DESCRIPTION: Create and convert precise mileage reimbursements into real Clockify flat expenses.
```

- [ ] **Step 5: Replace generic docs**

Replace `README.md` with:

- Product purpose.
- Non-goals.
- Required environment variables.
- Local run commands.
- Test commands.
- Manual developer workspace checklist pointer.

Replace the old generic docs with Mileage-specific short references:

- `endpoints.md`: list only `/api/mileage/*`, `/iframe/*`, lifecycle, webhook, and `/manifest`.
- `models.md`: list Mileage DTOs and state no mileage/rate/money DTO uses floating point.
- `webhooks.md`: list expense webhook behavior and loop prevention.
- `edge-cases.md`: list marker removal, duplicate delivery, locked expense, missing settings, receipt preservation.
- `reports.md`: state this add-on does not replace Clockify reports.

- [ ] **Step 6: Run smoke tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageApplicationSmokeTest test
```

Expected passing result:

```text
Failures: 0, Errors: 0
```

- [ ] **Step 7: Suggested commit message**

```bash
git add addon-expenses-rest-api/src/main/resources/application.yaml addon-expenses-rest-api/src/test/resources/application-test.yaml addon-expenses-rest-api/Dockerfile addon-expenses-rest-api/docker-compose.yml addon-expenses-rest-api/README.md addon-expenses-rest-api/endpoints.md addon-expenses-rest-api/models.md addon-expenses-rest-api/reports.md addon-expenses-rest-api/webhooks.md addon-expenses-rest-api/edge-cases.md
git commit -m "chore: productize runtime config and docs for mileage"
```

### Task 18: Remove Old Generic Expense API Surface

**Files:**
- Delete: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/ExpenseCategoryController.java`
- Delete: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/ExpenseController.java`
- Delete: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/ExpensesExceptionHandler.java`
- Delete: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/CategoryStatusRequest.java`
- Delete: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/CreateCategoryRequest.java`
- Delete: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/CreateExpenseRequest.java`
- Delete: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/DetailedReportRequest.java`
- Delete: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/Expense.java`
- Delete: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/ExpenseCategory.java`
- Delete: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/ExpenseCategoryListResponse.java`
- Delete: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/ExpenseListResponse.java`
- Delete: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/ExpenseReference.java`
- Delete: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/UpdateCategoryRequest.java`
- Delete: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/api/model/UpdateExpenseRequest.java`
- Delete: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses/config/ClockifyExpensesApiDbConfig.java`
- Delete: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/expenses/ClockifyExpensesApiIntegrationTest.java`
- Delete: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/expenses/ClockifyExpensesApiSecurityTest.java`
- Delete: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/expenses/ExpenseApiAdversarialTest.java`
- Delete: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/expenses/ExpenseApiTest.java`
- Delete: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/expenses/ManifestTest.java`

- [ ] **Step 1: Run compile to show duplicate and old-package failures**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -DskipTests compile
```

Expected failing result before cleanup:

```text
Compilation failure: old com.cake.clockify.addon.expenses references remain
```

- [ ] **Step 2: Remove old package tree**

Run:

```bash
git rm -r addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/expenses
git rm -r addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/expenses
```

If `git rm` reports a path does not exist, continue only after confirming `find addon-expenses-rest-api/src -path '*addon/expenses*' -print` returns no files.

- [ ] **Step 3: Scan for old product names**

Run:

```bash
rg -n "clockify-expenses-api|Clockify Expenses API|com\\.cake\\.clockify\\.addon\\.expenses|enableNotifications|maxExpenseAmount" addon-expenses-rest-api/src addon-expenses-rest-api/pom.xml addon-expenses-rest-api/README.md addon-expenses-rest-api/*.md
```

Expected passing result:

```text
no matches
```

- [ ] **Step 4: Compile**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -DskipTests compile
```

Expected passing result:

```text
BUILD SUCCESS
```

- [ ] **Step 5: Suggested commit message**

```bash
git add addon-expenses-rest-api
git commit -m "chore: remove generic expenses API boilerplate"
```

### Task 19: Security Hardening Tests

**Files:**
- Create or extend: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/MileageSecurityTest.java`
- Modify: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/MileageExceptionHandler.java`
- Modify: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/clockify/ClockifyExpenseGateway.java`
- Modify: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/MileageApiController.java`

- [ ] **Step 1: Add failing security tests**

Add:

```java
@Test void installationTokenNeverAppearsInIframeHtml()
@Test void installationTokenNeverAppearsInMileageApiResponse()
@Test void multipartCreateDropsAuthTokenAuthorizationAndAddonTokenFields()
@Test void errorsDoNotExposeStackTraceOrRawClockifyBody()
@Test void workspaceIsolationIsRequiredForConversionQueries()
@Test void fileUploadRejectsFilesLargerThanTenMegabytes()
@Test void fileUploadRejectsUnsafeContentTypeBeforeForwarding()
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageSecurityTest test
```

Expected failing result:

```text
expected response not to contain token value
```

- [ ] **Step 3: Implement file checks**

In `MileageApiController`, before forwarding multipart file:

- If file size is greater than `10 * 1024 * 1024`, throw `ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt file exceeds 10 MB")`.
- Allow content types `image/png`, `image/jpeg`, `image/gif`, `image/webp`, `image/heic`, and `application/pdf`.
- Reject blank or unlisted content type with `"Unsupported receipt file type"`.

- [ ] **Step 4: Ensure sanitized errors**

`MileageExceptionHandler` must map:

- `IllegalArgumentException` to `400` with `error` value `invalid_request`.
- `ResponseStatusException` to its status with safe reason.
- `ClockifyApiException` to `clockify_api_error`.
- Other exceptions to `500` with `internal_error`.

Do not include `Throwable.toString()` in response bodies.

- [ ] **Step 5: Run security tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageSecurityTest test
```

Expected passing result:

```text
Tests run: 13, Failures: 0, Errors: 0
```

- [ ] **Step 6: Suggested commit message**

```bash
git add addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/MileageSecurityTest.java
git commit -m "test: harden mileage token file and error handling"
```

### Task 20: Full Integration, Manifest, and Docker Verification

**Files:**
- No new production files.
- Update tests only if an assertion names a wrong method or path.

- [ ] **Step 1: Run targeted tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -am -Dtest=MileageManifestTest,MileageCalculatorTest,MileageNoteServiceTest,MileageEligibilityServiceTest,MileageConversionServiceTest,ClockifyExpenseGatewayTest,MileageApiControllerTest,MileageSettingsControllerTest,MileageConversionControllerTest,MileageWebhookIntegrationTest,MileageSecurityTest,MileageApplicationSmokeTest test
```

Expected passing result:

```text
BUILD SUCCESS
```

- [ ] **Step 2: Run full module tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -am test
```

Expected passing result:

```text
BUILD SUCCESS
```

- [ ] **Step 3: Run clean test**

Run:

```bash
mvn -pl addon-expenses-rest-api -am clean test
```

Expected passing result:

```text
BUILD SUCCESS
```

- [ ] **Step 4: Scan for forbidden floating point in mileage code**

Run:

```bash
rg -n "\\b(double|Double|float|Float)\\b" addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage
```

Expected passing result:

```text
no matches
```

- [ ] **Step 5: Scan for hardcoded Clockify API hosts in mileage code**

Run:

```bash
rg -n "api\\.clockify\\.me|reports\\.api\\.clockify\\.me|developer\\.clockify\\.me" addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage
```

Expected passing result:

```text
no matches in src/main/java; test matches are allowed only inside mock token claims
```

- [ ] **Step 6: Build Docker image**

Run:

```bash
docker compose -f addon-expenses-rest-api/docker-compose.yml build
```

Expected passing result:

```text
mileage-for-clockify-app  Built
```

- [ ] **Step 7: Start local stack**

Run:

```bash
docker compose -f addon-expenses-rest-api/docker-compose.yml up -d
```

Expected passing result:

```text
Container mileage-for-clockify-db   Healthy
Container mileage-for-clockify-app  Started
```

- [ ] **Step 8: Verify local health and manifest**

Run:

```bash
curl -fsS http://localhost:8080/healthz
curl -fsS http://localhost:8080/manifest | python3 -m json.tool >/tmp/mileage-manifest.json
python3 - <<'PY'
import json
p='/tmp/mileage-manifest.json'
m=json.load(open(p))
assert m['schemaVersion']=='1.5'
assert m['minimalSubscriptionPlan']=='PRO'
assert 'EXPENSE_READ' in m['scopes']
assert 'EXPENSE_WRITE' in m['scopes']
assert {w['event'] for w in m['webhooks']} >= {'EXPENSE_CREATED','EXPENSE_UPDATED','EXPENSE_DELETED','EXPENSE_RESTORED'}
print('manifest ok')
PY
```

Expected passing result:

```text
manifest ok
```

- [ ] **Step 9: Stop local stack**

Run:

```bash
docker compose -f addon-expenses-rest-api/docker-compose.yml down
```

Expected passing result:

```text
containers stopped and network removed
```

- [ ] **Step 10: Suggested commit message**

```bash
git add addon-expenses-rest-api
git commit -m "test: verify mileage addon end to end"
```

## 6. Manual Clockify Developer Workspace Validation

Run these steps after all automated verification passes:

1. Start the add-on with a public HTTPS tunnel.
2. Set `ADDON_BASE_URL` to the tunnel URL.
3. Set explicit `ADDON_CRYPTO_ACTIVE_KEY_ID` and `ADDON_CRYPTO_KEY_K1`.
4. Open `{ADDON_BASE_URL}/manifest` and verify `schemaVersion` is `"1.5"`.
5. Create a private Clockify add-on in the developer portal with manifest URL `{ADDON_BASE_URL}/manifest`.
6. Install it in a sacrificial developer workspace.
7. Confirm `/lifecycle/installed` succeeds and `addon_installations` contains one active row for the workspace.
8. Open the Mileage sidebar.
9. Confirm the URL briefly has `auth_token`.
10. Confirm the iframe removes `auth_token` from the visible URL after load.
11. As admin, open Admin Settings.
12. Select or enter an input unit mileage category.
13. Select or enter an output flat mileage category.
14. Save rate `0.655`, unit `mi`, rounding `HALF_UP`, `convertOnCreate=true`, `convertOnUpdate=true`, `preserveOriginalNotes=true`, and `dryRunMode=false`.
15. Open Diagnostics and verify no missing settings warning remains.
16. Open Create Mileage.
17. Submit date, user, project, task, miles `37.4`, billable flag, and note `Client site visit`.
18. Verify the add-on response shows rounded amount `24.50`.
19. Verify Clockify Expenses contains a real flat expense in the output category.
20. Verify the Clockify expense note contains `Mileage reimbursement` and `[MileageAddon:converted:v1 id=`.
21. Submit another add-on mileage expense with a receipt file.
22. Verify the receipt appears on the real Clockify expense.
23. In Clockify web, create a native expense in the input unit category with quantity `37.4`.
24. Wait for `EXPENSE_CREATED`.
25. Verify the same Clockify expense changes to the output category and amount `24.50`.
26. Verify the receipt remains present if the native expense had a receipt.
27. Confirm the resulting `EXPENSE_UPDATED` webhook is ignored and no second conversion row appears.
28. Create a native expense in a non-input category and verify it is skipped.
29. Delete a converted expense and verify the conversion row status becomes `DELETED`.
30. Restore that expense and verify it is not converted again when marker remains.
31. Restore an unconverted input-category expense and verify it converts once.
32. Lock, approve, invoice, or otherwise finalize an expense when the developer workspace supports the action.
33. Trigger conversion for the finalized expense and verify the add-on records `FINALIZED_OR_LOCKED` or sanitized `FAILED`.
34. Disable Mileage in settings.
35. Create a native input-category expense and verify webhook processing records no mutation.
36. Uninstall the add-on.
37. Verify `addon_installations` is inactive or removed according to addon-db behavior.
38. Verify `mileage_workspace_settings` and `mileage_conversion` rows for that workspace are removed by uninstall cleanup.

## 7. Live Sacrificial Workspace API Evidence Gate

Run this gate when shell environment variables are available for a sacrificial Clockify developer workspace. This gate is intentionally separate from automated tests because it mutates real Clockify test data.

Required environment variables:

```text
CLOCKIFY_API_BASE_URL
CLOCKIFY_API_KEY
CLOCKIFY_WORKSPACE_ID
CLOCKIFY_TEST_USER_ID
```

Optional environment variables:

```text
CLOCKIFY_TEST_PROJECT_ID
CLOCKIFY_TEST_TASK_ID
```

Never print `CLOCKIFY_API_KEY`.

Run this preflight from `/Users/15x/Downloads/WORKING/addons-me/addonFactory`:

```bash
python3 - <<'PY'
import os
required = ["CLOCKIFY_API_BASE_URL", "CLOCKIFY_API_KEY", "CLOCKIFY_WORKSPACE_ID", "CLOCKIFY_TEST_USER_ID"]
missing = [name for name in required if not os.environ.get(name)]
if missing:
    raise SystemExit("missing required env vars: " + ", ".join(missing))
print("clockify live env ok: " + ", ".join(name for name in required))
PY
```

Expected passing result:

```text
clockify live env ok: CLOCKIFY_API_BASE_URL, CLOCKIFY_API_KEY, CLOCKIFY_WORKSPACE_ID, CLOCKIFY_TEST_USER_ID
```

Run this live API shape and receipt-preservation probe:

```bash
python3 - <<'PY'
import base64
import json
import os
import subprocess
import tempfile
import time
import urllib.request

base = os.environ["CLOCKIFY_API_BASE_URL"].rstrip("/")
api_key = os.environ["CLOCKIFY_API_KEY"]
workspace_id = os.environ["CLOCKIFY_WORKSPACE_ID"]
user_id = os.environ["CLOCKIFY_TEST_USER_ID"]
project_id = os.environ.get("CLOCKIFY_TEST_PROJECT_ID", "")
task_id = os.environ.get("CLOCKIFY_TEST_TASK_ID", "")
stamp = str(int(time.time()))
unit_category_id = None
flat_category_id = None
expense_id = None

def curl_json(method, path, body=None):
    cmd = [
        "curl", "-fsS", "-X", method,
        "-H", f"X-Api-Key: {api_key}",
        "-H", "Content-Type: application/json",
        f"{base}{path}",
    ]
    if body is not None:
        cmd.extend(["--data", json.dumps(body)])
    out = subprocess.check_output(cmd, text=True)
    return json.loads(out) if out.strip() else None

def curl_empty(method, path, body=None):
    cmd = [
        "curl", "-fsS", "-X", method,
        "-H", f"X-Api-Key: {api_key}",
        "-H", "Content-Type: application/json",
        f"{base}{path}",
    ]
    if body is not None:
        cmd.extend(["--data", json.dumps(body)])
    subprocess.check_call(cmd)

try:
    unit_category = curl_json("POST", f"/v1/workspaces/{workspace_id}/expenses/categories", {
        "name": f"MILEAGE-LIVE-UNIT-{stamp}",
        "hasUnitPrice": True,
        "priceInCents": 1,
        "unit": "mi"
    })
    unit_category_id = unit_category["id"]

    flat_category = curl_json("POST", f"/v1/workspaces/{workspace_id}/expenses/categories", {
        "name": f"MILEAGE-LIVE-FLAT-{stamp}",
        "hasUnitPrice": False,
        "priceInCents": 0,
        "unit": ""
    })
    flat_category_id = flat_category["id"]

    png_path = tempfile.NamedTemporaryFile(prefix="mileage-live-", suffix=".png", delete=False).name
    with open(png_path, "wb") as f:
        f.write(base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="))

    create_cmd = [
        "curl", "-fsS", "-X", "POST",
        "-H", f"X-Api-Key: {api_key}",
        "-F", f"categoryId={unit_category_id}",
        "-F", f"userId={user_id}",
        "-F", "amount=37.4",
        "-F", "date=2026-05-24T12:00:00Z",
        "-F", "notes=MILEAGE-LIVE-PROBE original note",
        "-F", "billable=false",
        "-F", f"file=@{png_path};type=image/png",
        f"{base}/v1/workspaces/{workspace_id}/expenses",
    ]
    if project_id:
        create_cmd[6:6] = ["-F", f"projectId={project_id}"]
    if task_id:
        create_cmd[6:6] = ["-F", f"taskId={task_id}"]
    created = json.loads(subprocess.check_output(create_cmd, text=True))
    expense_id = created["id"]

    fetched = curl_json("GET", f"/v1/workspaces/{workspace_id}/expenses/{expense_id}")
    print("created_shape=" + json.dumps({
        "has_id": "id" in fetched,
        "has_workspaceId": "workspaceId" in fetched,
        "has_quantity": "quantity" in fetched,
        "has_total": "total" in fetched,
        "has_fileId": "fileId" in fetched,
        "locked": fetched.get("locked")
    }, sort_keys=True))

    updated = curl_json("PUT", f"/v1/workspaces/{workspace_id}/expenses/{expense_id}", {
        "categoryId": flat_category_id,
        "amount": "24.50",
        "notes": "MILEAGE-LIVE-PROBE converted [MileageAddon:converted:v1 id=00000000-0000-0000-0000-000000000000]",
        "changeFields": "CATEGORY,AMOUNT,NOTES"
    })
    refetched = curl_json("GET", f"/v1/workspaces/{workspace_id}/expenses/{expense_id}")
    print("updated_shape=" + json.dumps({
        "categoryId": refetched.get("categoryId"),
        "quantity": str(refetched.get("quantity")),
        "total": str(refetched.get("total")),
        "fileId_preserved": bool(refetched.get("fileId")),
        "locked": refetched.get("locked")
    }, sort_keys=True))
finally:
    if expense_id:
        try:
            curl_empty("DELETE", f"/v1/workspaces/{workspace_id}/expenses/{expense_id}")
        except Exception as exc:
            print("cleanup_expense_failed_status_only")
    for category_id in [unit_category_id, flat_category_id]:
        if category_id:
            try:
                curl_empty("PATCH", f"/v1/workspaces/{workspace_id}/expenses/categories/{category_id}/status", {"archived": True})
                curl_empty("DELETE", f"/v1/workspaces/{workspace_id}/expenses/categories/{category_id}")
            except Exception:
                print("cleanup_category_failed_status_only")
PY
```

Expected passing result:

```text
created_shape={"has_fileId": true, "has_id": true, "has_quantity": true, "has_total": true, "has_workspaceId": true, "locked": false}
updated_shape={"categoryId": "<flat category id>", "fileId_preserved": true, "locked": false, "quantity": "1.0", "total": "2450.0"}
```

If the live output differs:

- If `fileId_preserved` is `false`, update Task 11 to preserve receipt explicitly by including the receipt-safe fields required by Clockify or by blocking native conversion for receipt-bearing expenses until receipt preservation is solved.
- If `quantity` or `total` fields are missing, update `ClockifyExpenseGateway.getExpense` and `ClockifyExpenseSnapshot` before implementing conversion.
- If category update returns a conflict or locked response, keep the Task 11 behavior that records sanitized `FAILED`; add a test for the exact status code observed.
- If category cleanup fails because Clockify requires a different archive/delete sequence, leave the sacrificial categories archived and record their IDs in the implementation PR notes.

## 8. Final Readiness Checklist

- [ ] `addon-java-sdk/` remains unmodified.
- [ ] `addon-core/` remains unmodified.
- [ ] `addon-db/` remains unmodified.
- [ ] `/manifest` returns valid JSON.
- [ ] `/manifest` validates against local schema `manifest-schema.json` version `1.5`.
- [ ] Manifest has `schemaVersion: "1.5"`.
- [ ] Manifest has `minimalSubscriptionPlan: "PRO"`.
- [ ] Manifest has scopes `EXPENSE_READ`, `EXPENSE_WRITE`, `USER_READ`, `PROJECT_READ`, and `WORKSPACE_READ`.
- [ ] Manifest has webhooks `EXPENSE_CREATED`, `EXPENSE_UPDATED`, `EXPENSE_DELETED`, and `EXPENSE_RESTORED`.
- [ ] Manifest has sidebar component path `/iframe/mileage`.
- [ ] Installation lifecycle stores token through addon-db only.
- [ ] No installation token is sent to frontend.
- [ ] All settings and conversion queries filter by `workspaceId`.
- [ ] No mileage package code uses `double`, `Double`, `float`, or `Float`.
- [ ] Calculator test proves `37.4 * 0.655` gives calculated `24.4970` and rounded `24.50`.
- [ ] Add-on-created expense writes a real Clockify flat expense.
- [ ] Native/mobile-created input-category expense converts exactly once.
- [ ] `EXPENSE_UPDATED` caused by conversion does not loop.
- [ ] `EXPENSE_DELETED` marks audit `DELETED` without hard-deleting the row.
- [ ] `EXPENSE_RESTORED` rechecks eligibility.
- [ ] Locked/finalized conflicts are recorded with sanitized failure details.
- [ ] Iframe UI has no inline script, inline style, or inline event handlers.
- [ ] Admin-only APIs reject non-admin roles.
- [ ] Receipt upload is bounded to 10 MB and allowed content types.
- [ ] Error responses do not contain stack traces, tokens, raw upstream bodies, or receipt bytes.
- [ ] `mvn -pl addon-expenses-rest-api -am clean test` passes.
- [ ] `docker compose -f addon-expenses-rest-api/docker-compose.yml build` passes.
- [ ] Manual developer workspace validation is complete.
- [ ] Live sacrificial workspace API evidence gate is complete, or a PR note explains why the required env vars were unavailable.

## 9. Acceptance Criteria Mapping

- Manifest and installation criteria map to Tasks 2, 3, 16, 20, and manual validation steps 4 through 7.
- Settings criteria map to Tasks 6, 14, 15, and manual validation steps 11 through 15.
- Calculation criteria map to Task 5 and Task 20 forbidden floating-point scan.
- Add-on-created expense criteria map to Tasks 7, 8, 10, 19, and manual validation steps 16 through 22.
- Native/mobile conversion criteria map to Tasks 9, 11, 12, 13, manual validation steps 23 through 28, and the live API evidence gate.
- Webhook loop prevention criteria map to Tasks 9, 11, 12, and manual validation step 27.
- Deleted/restored behavior criteria map to Task 13 and manual validation steps 29 through 31.
- Finalized/locked records criteria map to Tasks 9, 11, 19, and manual validation steps 32 through 33.
- Security criteria map to Tasks 3, 6, 7, 14, 15, 16, 19, and Task 20 scans.
- UI criteria map to Task 15 and manual validation steps 8 through 15.
- Testing and CI criteria map to Tasks 2 through 20, the live API evidence gate, and the final readiness checklist.

## 10. Residual Implementation Risks to Verify Live

- Receipt preservation on an update from unit category to flat category must be verified in the developer workspace. Automated tests must assert that the add-on does not send a file deletion or replacement during conversion.
- Fetched expense payloads may expose finalized, approved, invoiced, or locked state with fields beyond `locked`. The implementation must skip when `locked=true` and must record sanitized `FAILED` if Clockify rejects update with a conflict or permission response.
- User-token role claims must include `workspaceRole` for clean admin UI gating. Backend admin APIs must remain authoritative even if the frontend cannot decode the role.
