# Mileage Final Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix every actionable item from the final DB, stability, webhook, and UI/UX review, then run an adversarial review and push only after the review is clean.

**Architecture:** Keep Clockify as the expense ledger and keep the add-on DB limited to settings, webhook/event records, and conversion auditing. Use small, typed Spring/JPA changes for backend race and visibility fixes, one forward Flyway migration for DB defaults, and focused vanilla JavaScript changes for UI behavior. Do not add new frameworks, proxy layers, or parallel expense state.

**Tech Stack:** Java 21, Spring Boot 3.x, Spring Web MVC, Spring Data JPA/Hibernate, Flyway, PostgreSQL `numeric`, Jackson, Jakarta Bean Validation, vanilla server-served HTML/CSS/JavaScript, Maven, Docker Compose.

---

## Execution Protocol For A Non-SOTA Worker

- Work from repository root: `/Users/15x/Downloads/WORKING/addons-me/mileage-for-clockify`.
- Start with `git status --short --branch`; do not overwrite user changes.
- Read `AGENTS.md`, `CLAUDE.md`, this plan, and the files named in each task before editing.
- Use `BigDecimal` for mileage/rates/money. Do not introduce `double`, `Double`, `float`, or `Float`.
- Do not edit `addon-expenses-rest-api/addon-java-sdk/` or `repo/`.
- Do not hardcode Clockify API URLs.
- Do not expose installation tokens or crypto keys to frontend code or logs.
- Keep every repository query and service call workspace-scoped unless the row is an internal webhook-event row already identified by UUID.
- Run the focused test after each task. Do not weaken tests to make them pass.
- Commit after each completed task or tightly related pair of tasks.
- After all tasks, run the adversarial review section. Push to `main` only after it is clean.

## File Structure

- Modify `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/MileageApiController.java` to make manual-create audit persistence idempotent after Clockify creates the ledger expense.
- Modify `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/conversion/MileageConversionService.java` to reserve one audit row per workspace/expense atomically and make failed rows visible by date.
- Create `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/audit/MileageConversionReservationRepository.java` for the minimal PostgreSQL upsert/row-lock needed by conversion auditing.
- Modify `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/audit/MileageConversionRepository.java` only if a workspace-scoped helper is needed after reservation.
- Create `addon-expenses-rest-api/src/main/resources/db/migration/V16__align_mileage_settings_defaults.sql` to align DB defaults with current product defaults.
- Modify `addon-core/src/main/java/com/cake/clockify/addon/core/webhook/WebhookController.java` so event-service failures before handler dispatch do not return blind 5xx retries.
- Modify `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/MileageConversionController.java` to neutralize CSV formula injection.
- Modify `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/clockify/ClockifyExpenseGateway.java` to bound option pagination.
- Modify `addon-expenses-rest-api/src/main/resources/static/assets/mileage/settings.js` for settings-load resilience, custom-date validation, receipt preflight validation, invalid-date formatting, and category-repair busy state.
- Modify tests beside the changed code:
  - `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/api/MileageApiControllerTest.java`
  - `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/conversion/MileageConversionServiceTest.java`
  - `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/settings/MileageSettingsServiceTest.java`
  - `addon-core/src/test/java/com/cake/clockify/addon/core/webhook/WebhookControllerTest.java`
  - `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/api/MileageConversionControllerTest.java`
  - `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/clockify/ClockifyExpenseGatewayTest.java`
  - Add a JS/static regression test only if an existing pattern exists; otherwise keep UI changes tiny and verify through source review plus full Maven tests.
- Update `CLAUDE.md` only with durable guidance learned from this pass. Do not add a narrative changelog.

---

### Task 1: Pre-Flight And Baseline

**Files:**
- Read: `AGENTS.md`
- Read: `CLAUDE.md`
- Read: `addon-expenses-rest-api/README.md`
- Read: `addon-expenses-rest-api/webhooks.md`
- Read: `docs/superpowers/plans/2026-05-26-mileage-final-hardening.md`

- [ ] **Step 1: Confirm branch and dirty state**

Run:

```bash
git status --short --branch
git log --oneline --decorate -5
```

Expected:
- You are on `main`.
- Existing dirty files are understood before editing.
- Do not run `git reset --hard` or `git checkout --`.

- [ ] **Step 2: Run the baseline test suite**

Run:

```bash
mvn -pl addon-expenses-rest-api -am test
```

Expected:
- Reactor `BUILD SUCCESS`.
- If this fails before edits, stop and diagnose the pre-existing failure before changing product code.

- [ ] **Step 3: Commit only if baseline docs/plan changes are intentionally being saved**

If this plan and `CLAUDE.md` are already committed, skip this step. If they are uncommitted and the user asked this session to include them, run:

```bash
git add CLAUDE.md docs/superpowers/plans/2026-05-26-mileage-final-hardening.md
git commit -m "docs: add final mileage hardening plan"
```

Expected:
- Commit succeeds.
- Do not push yet.

---

### Task 2: Add Atomic Audit Reservation

**Files:**
- Create: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/audit/MileageConversionReservationRepository.java`
- Modify: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/settings/MileageSettingsServiceTest.java`

- [ ] **Step 1: Write the failing reservation integration test**

In `MileageSettingsServiceTest`, add `@Autowired MileageConversionReservationRepository reservationRepository;` next to the existing repository fields.

Add this test:

```java
@Test
void conversionReservationReturnsSameIdForDuplicateWorkspaceExpense() {
    UUID first = reservationRepository.reserve(
            "ws-reserve",
            "exp-reserve",
            MileageConversionSource.WEBHOOK_CREATED,
            "EXPENSE_CREATED");
    UUID second = reservationRepository.reserve(
            "ws-reserve",
            "exp-reserve",
            MileageConversionSource.WEBHOOK_UPDATED,
            "EXPENSE_UPDATED");

    assertThat(second).isEqualTo(first);
    assertThat(conversionRepository.countByWorkspaceIdAndExpenseId("ws-reserve", "exp-reserve"))
            .isEqualTo(1);
    MileageConversion conversion = conversionRepository
            .findByWorkspaceIdAndExpenseId("ws-reserve", "exp-reserve")
            .orElseThrow();
    assertThat(conversion.getSource()).isEqualTo(MileageConversionSource.WEBHOOK_CREATED);
    assertThat(conversion.getStatus()).isEqualTo(MileageConversionStatus.RECEIVED);
}
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
mvn -pl addon-expenses-rest-api -Dtest=MileageSettingsServiceTest#conversionReservationReturnsSameIdForDuplicateWorkspaceExpense test
```

Expected:
- FAIL because `MileageConversionReservationRepository` does not exist.

- [ ] **Step 3: Implement the minimal reservation repository**

Create `MileageConversionReservationRepository.java`:

```java
package com.cake.clockify.addon.mileage.audit;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public class MileageConversionReservationRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public UUID reserve(
            String workspaceId,
            String expenseId,
            MileageConversionSource source,
            String sourceEventType) {
        UUID id = UUID.randomUUID();
        Object result = entityManager.createNativeQuery("""
                INSERT INTO mileage_conversion (
                    id, workspace_id, expense_id, source, source_event_type, status, created_at, updated_at
                )
                VALUES (
                    :id, :workspaceId, :expenseId, :source, :sourceEventType, 'RECEIVED', NOW(), NOW()
                )
                ON CONFLICT (workspace_id, expense_id) DO UPDATE
                    SET updated_at = mileage_conversion.updated_at
                RETURNING id
                """)
                .setParameter("id", id)
                .setParameter("workspaceId", workspaceId)
                .setParameter("expenseId", expenseId)
                .setParameter("source", source.name())
                .setParameter("sourceEventType", sourceEventType)
                .getSingleResult();
        return result instanceof UUID uuid ? uuid : UUID.fromString(result.toString());
    }
}
```

- [ ] **Step 4: Run the focused integration test**

Run:

```bash
mvn -pl addon-expenses-rest-api -Dtest=MileageSettingsServiceTest#conversionReservationReturnsSameIdForDuplicateWorkspaceExpense test
```

Expected:
- PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/audit/MileageConversionReservationRepository.java \
  addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/settings/MileageSettingsServiceTest.java
git commit -m "fix: reserve mileage audit rows atomically"
```

---

### Task 3: Use Reservation In Native Webhook Conversion

**Files:**
- Modify: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/conversion/MileageConversionService.java`
- Modify: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/conversion/MileageConversionServiceTest.java`

- [ ] **Step 1: Write failing service tests**

Update test setup to mock `MileageConversionReservationRepository` and pass it to the service constructor:

```java
private MileageConversionReservationRepository reservationRepository;
private final java.time.Clock clock = java.time.Clock.fixed(
        Instant.parse("2026-05-27T12:00:00Z"),
        java.time.ZoneOffset.UTC);
```

In `setUp()`:

```java
reservationRepository = mock(MileageConversionReservationRepository.class);
service = new MileageConversionService(
        settingsService,
        gateway,
        conversionRepository,
        reservationRepository,
        new MileageEligibilityService(),
        new MileageCalculator(),
        new MileageNoteService(),
        clock);
```

For existing tests that create a new conversion, add this helper and call it before invoking the service:

```java
private MileageConversion reservedConversion(String expenseId, MileageConversionSource source, String eventType) {
    MileageConversion conversion = new MileageConversion();
    conversion.setId(UUID.randomUUID());
    conversion.setWorkspaceId("ws-native");
    conversion.setExpenseId(expenseId);
    conversion.setSource(source);
    conversion.setSourceEventType(eventType);
    conversion.setStatus(MileageConversionStatus.RECEIVED);
    when(reservationRepository.reserve("ws-native", expenseId, source, eventType)).thenReturn(conversion.getId());
    when(conversionRepository.findByIdAndWorkspaceId(conversion.getId(), "ws-native")).thenReturn(Optional.of(conversion));
    return conversion;
}
```

Add this test:

```java
@Test
void failedFetchKeepsAuditRowVisibleWithFallbackExpenseDate() throws Exception {
    reservedConversion("exp-fail", MileageConversionSource.WEBHOOK_CREATED, "EXPENSE_CREATED");
    when(settingsService.validateForNativeConversion("ws-native")).thenReturn(settings(false));
    when(gateway.getExpense("ws-native", "exp-fail"))
            .thenThrow(new java.io.IOException("network down"));

    ConversionResult result = service.convertIfEligible(
            claims(),
            "exp-fail",
            MileageConversionSource.WEBHOOK_CREATED,
            "EXPENSE_CREATED");

    assertThat(result.status()).isEqualTo(MileageConversionStatus.FAILED);
    ArgumentCaptor<MileageConversion> saved = ArgumentCaptor.forClass(MileageConversion.class);
    verify(conversionRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getExpenseDate()).isEqualTo(LocalDate.parse("2026-05-27"));
}
```

- [ ] **Step 2: Run the failing focused test**

Run:

```bash
mvn -pl addon-expenses-rest-api -Dtest=MileageConversionServiceTest test
```

Expected:
- FAIL because the service constructor and reservation behavior are not implemented yet.

- [ ] **Step 3: Implement reservation use and fallback failure date**

In `MileageConversionService`, add fields:

```java
private final MileageConversionReservationRepository reservationRepository;
private final java.time.Clock clock;
```

Update the constructor to accept both fields:

```java
public MileageConversionService(
        MileageSettingsService settingsService,
        ClockifyExpenseGateway gateway,
        MileageConversionRepository conversionRepository,
        MileageConversionReservationRepository reservationRepository,
        MileageEligibilityService eligibilityService,
        MileageCalculator calculator,
        MileageNoteService noteService,
        java.time.Clock clock) {
    this.settingsService = settingsService;
    this.gateway = gateway;
    this.conversionRepository = conversionRepository;
    this.reservationRepository = reservationRepository;
    this.eligibilityService = eligibilityService;
    this.calculator = calculator;
    this.noteService = noteService;
    this.clock = clock;
}
```

Replace the start of `convertIfEligible()` after the blank expense ID check with:

```java
UUID reservedId = reservationRepository.reserve(claims.workspaceId(), cleanedExpenseId, source, sourceEventType);
MileageConversion conversion = conversionRepository.findByIdAndWorkspaceId(reservedId, claims.workspaceId())
        .orElseThrow(() -> new IllegalStateException("Reserved mileage conversion was not found"));
boolean wasSuccessfullyConverted = isSuccessfullyConverted(Optional.of(conversion));
```

Remove the later `findByWorkspaceIdAndExpenseId(...)` call. Replace uses of `existing` with `Optional.of(conversion)` only where the existing status must be passed to helper methods, and use `wasSuccessfullyConverted` for eligibility.

In `fail(...)`, before saving:

```java
if (conversion.getExpenseDate() == null) {
    conversion.setExpenseDate(LocalDate.now(clock));
}
```

- [ ] **Step 4: Update the existing tests to reserve rows**

Every test in `MileageConversionServiceTest` that expects normal conversion behavior must call `reservedConversion(...)` before `service.convertIfEligible(...)`.

For tests with existing converted rows, create the row with `status=CONVERTED`, mock the reservation to return that existing ID, and mock `findByIdAndWorkspaceId`.

Use this helper:

```java
private MileageConversion reservedExisting(
        String expenseId,
        MileageConversionStatus status,
        MileageConversionSource source,
        String eventType) {
    MileageConversion conversion = existing(status);
    conversion.setExpenseId(expenseId);
    when(reservationRepository.reserve("ws-native", expenseId, source, eventType)).thenReturn(conversion.getId());
    when(conversionRepository.findByIdAndWorkspaceId(conversion.getId(), "ws-native")).thenReturn(Optional.of(conversion));
    return conversion;
}
```

- [ ] **Step 5: Run focused conversion tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -Dtest=MileageConversionServiceTest test
```

Expected:
- PASS.

- [ ] **Step 6: Commit**

Run:

```bash
git add addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/conversion/MileageConversionService.java \
  addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/conversion/MileageConversionServiceTest.java
git commit -m "fix: make native mileage conversion idempotent"
```

---

### Task 4: Use Reservation In Manual Create Audit Persistence

**Files:**
- Modify: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/MileageApiController.java`
- Modify: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/api/MileageApiControllerTest.java`

- [ ] **Step 1: Write the failing manual-create merge test**

Mock `MileageConversionReservationRepository` in `MileageApiControllerTest`, pass it into the controller constructor, and add:

```java
@Test
void createExpenseMergesAuditRowWhenWebhookReservedExpenseFirst() throws Exception {
    when(settingsService.validateForAddonCreate("ws-api")).thenReturn(completeSettings(false));
    when(gateway.createFlatExpense(eq("ws-api"), any(CreateFlatExpenseCommand.class))).thenReturn(createdExpense("exp-race"));
    UUID existingId = UUID.fromString("00000000-0000-0000-0000-000000000123");
    MileageConversion existing = new MileageConversion();
    existing.setId(existingId);
    existing.setWorkspaceId("ws-api");
    existing.setExpenseId("exp-race");
    existing.setSource(MileageConversionSource.WEBHOOK_CREATED);
    existing.setStatus(MileageConversionStatus.SKIPPED);
    when(reservationRepository.reserve(eq("ws-api"), eq("exp-race"), eq(MileageConversionSource.ADDON_FORM), eq("ADDON_FORM")))
            .thenReturn(existingId);
    when(conversionRepository.findByIdAndWorkspaceId(existingId, "ws-api")).thenReturn(java.util.Optional.of(existing));
    when(conversionRepository.saveAndFlush(any(MileageConversion.class))).thenAnswer(invocation -> invocation.getArgument(0));

    mockMvc.perform(post("/api/mileage/expenses")
                    .header("Authorization", "Bearer valid-user-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMileage("37.4", "")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.expenseId").value("exp-race"))
            .andExpect(jsonPath("$.conversionId").value(existingId.toString()));

    ArgumentCaptor<MileageConversion> saved = ArgumentCaptor.forClass(MileageConversion.class);
    verify(conversionRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getId()).isEqualTo(existingId);
    assertThat(saved.getValue().getSource()).isEqualTo(MileageConversionSource.ADDON_FORM);
    assertThat(saved.getValue().getStatus()).isEqualTo(MileageConversionStatus.CONVERTED);
    assertThat(saved.getValue().getUserId()).isEqualTo("user-claims");
}
```

- [ ] **Step 2: Run the failing focused API test**

Run:

```bash
mvn -pl addon-expenses-rest-api -Dtest=MileageApiControllerTest#createExpenseMergesAuditRowWhenWebhookReservedExpenseFirst test
```

Expected:
- FAIL because the controller does not use the reservation repository yet.

- [ ] **Step 3: Implement manual-create merge**

Inject `MileageConversionReservationRepository` into `MileageApiController`.

After Clockify returns `expenseId`, reserve the audit row before populating it:

```java
UUID persistedId = reservationRepository.reserve(
        workspaceId,
        expenseId,
        MileageConversionSource.ADDON_FORM,
        "ADDON_FORM");
MileageConversion conversion = conversionRepository.findByIdAndWorkspaceId(persistedId, workspaceId)
        .orElseThrow(() -> new IllegalStateException("Reserved mileage conversion was not found"));
applyAddonFormConversion(
        conversion,
        workspaceId,
        expenseId,
        userId,
        request,
        expenseDate,
        settings,
        calculation,
        noteService.marker(conversionId));
conversionRepository.saveAndFlush(conversion);
```

Convert the existing `conversion(...)` factory into a mutating helper named `applyAddonFormConversion(...)` so it can update a pre-existing reserved row without changing its primary key.

Return the persisted audit row ID:

```java
return new MileageCreateExpenseResponse(
        expenseId,
        conversion.getId(),
        calculation.milesText(),
        calculation.rateText(),
        calculation.calculatedAmountText(),
        calculation.roundedAmountText());
```

- [ ] **Step 4: Run focused API tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -Dtest=MileageApiControllerTest test
```

Expected:
- PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/MileageApiController.java \
  addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/api/MileageApiControllerTest.java
git commit -m "fix: make manual mileage audit writes idempotent"
```

---

### Task 5: Align Database Defaults

**Files:**
- Create: `addon-expenses-rest-api/src/main/resources/db/migration/V16__align_mileage_settings_defaults.sql`
- Modify: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/settings/MileageSettingsServiceTest.java`

- [ ] **Step 1: Write a failing DB-default test**

Add to `MileageSettingsServiceTest`:

```java
@Autowired org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

@Test
void databaseDefaultsMatchSingleMileageCategoryDefaults() {
    jdbcTemplate.update("INSERT INTO mileage_test.mileage_workspace_settings (workspace_id) VALUES (?)", "ws-db-defaults");

    Map<String, Object> row = jdbcTemplate.queryForMap("""
            SELECT unit, rounding_mode, preserve_original_notes
            FROM mileage_test.mileage_workspace_settings
            WHERE workspace_id = ?
            """, "ws-db-defaults");

    assertThat(row.get("unit")).isEqualTo("mile");
    assertThat(row.get("rounding_mode")).isEqualTo("HALF_UP");
    assertThat(row.get("preserve_original_notes")).isEqualTo(false);
}
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
mvn -pl addon-expenses-rest-api -Dtest=MileageSettingsServiceTest#databaseDefaultsMatchSingleMileageCategoryDefaults test
```

Expected:
- FAIL because the DB default for `unit` is `mi` and `preserve_original_notes` is true.

- [ ] **Step 3: Add the forward migration**

Create `V16__align_mileage_settings_defaults.sql`:

```sql
ALTER TABLE mileage_workspace_settings
    ALTER COLUMN unit SET DEFAULT 'mile',
    ALTER COLUMN preserve_original_notes SET DEFAULT FALSE,
    ALTER COLUMN rounding_mode SET DEFAULT 'HALF_UP';
```

- [ ] **Step 4: Run the focused DB test**

Run:

```bash
mvn -pl addon-expenses-rest-api -Dtest=MileageSettingsServiceTest#databaseDefaultsMatchSingleMileageCategoryDefaults test
```

Expected:
- PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add addon-expenses-rest-api/src/main/resources/db/migration/V16__align_mileage_settings_defaults.sql \
  addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/settings/MileageSettingsServiceTest.java
git commit -m "fix: align mileage settings database defaults"
```

---

### Task 6: Acknowledge Webhook Event-Service Failures Safely

**Files:**
- Modify: `addon-core/src/main/java/com/cake/clockify/addon/core/webhook/WebhookController.java`
- Modify: `addon-core/src/test/java/com/cake/clockify/addon/core/webhook/WebhookControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

Add three tests to `WebhookControllerTest`:

```java
@Test
void dedupeLookupFailureIsAcknowledgedWithoutCallingHandler() {
    WebhookEventService eventService = mock(WebhookEventService.class);
    byte[] body = "{\"expenseId\":\"exp-1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    when(eventService.isDuplicate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
            .thenThrow(new IllegalStateException("db down"));
    AcknowledgingExpenseWebhookHandler handler = new AcknowledgingExpenseWebhookHandler();
    WebhookController controller = new WebhookController(List.of(handler), properties(), new ObjectMapper(), eventService);

    ResponseEntity<Void> response = controller.handleWebhook(request("EXPENSE_CREATED"), body);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(handler.calls).isZero();
}
```

Change `AcknowledgingExpenseWebhookHandler` to track calls:

```java
int calls;

@Override
public void handle(NormalizedClaims claims, String eventType, byte[] rawBody) {
    calls++;
}
```

Add equivalent tests for `recordEvent(...)` throwing and `tryStartProcessing(...)` throwing.

- [ ] **Step 2: Run the failing tests**

Run:

```bash
mvn -pl addon-core -Dtest=WebhookControllerTest test
```

Expected:
- FAIL because exceptions before handler dispatch escape.

- [ ] **Step 3: Wrap the event-service phase**

In `WebhookController.handleWebhook`, wrap lines that call `isDuplicate`, `recordEvent`, and `tryStartProcessing` in a `try/catch`.

Use this behavior:

```java
try {
    Optional<String> dedupeKeyOpt = WebhookDedupeKey.from(eventType, body, objectMapper);
    if (dedupeKeyOpt.isPresent()) {
        String dedupeKey = dedupeKeyOpt.get();
        if (eventService.isDuplicate(properties.key(), claims.workspaceId(), dedupeKey)) {
            log.info("webhook.handler.duplicate: workspace={} event={} dedupeKey={}",
                    claims.workspaceId(), eventType, dedupeKey);
            return ResponseEntity.ok().build();
        }

        String payloadHash = WebhookDedupeKey.payloadHash(body);
        eventId = eventService.recordEvent(properties.key(), claims.workspaceId(), eventType, dedupeKey, payloadHash);

        if (eventId != null && !eventService.tryStartProcessing(eventId)) {
            log.info("webhook.handler.processing-conflict: eventId={}", eventId);
            return ResponseEntity.ok().build();
        }
    }
} catch (Exception e) {
    log.error("webhook.handler.audit-start-failed: workspace={} event={}", claims.workspaceId(), eventType, e);
    return ResponseEntity.ok().build();
}
```

Do not call the target handler when the event audit/dedupe layer cannot establish whether this delivery is duplicate or already processing.

- [ ] **Step 4: Run focused tests**

Run:

```bash
mvn -pl addon-core -Dtest=WebhookControllerTest test
```

Expected:
- PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add addon-core/src/main/java/com/cake/clockify/addon/core/webhook/WebhookController.java \
  addon-core/src/test/java/com/cake/clockify/addon/core/webhook/WebhookControllerTest.java
git commit -m "fix: acknowledge webhook audit startup failures"
```

---

### Task 7: Neutralize CSV Formula Injection

**Files:**
- Modify: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/MileageConversionController.java`
- Modify: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/api/MileageConversionControllerTest.java`

- [ ] **Step 1: Write a failing CSV test**

Add a conversion whose `expenseId` or `userId` starts with `=` and assert exported CSV prefixes it with an apostrophe.

Example assertion:

```java
mockMvc.perform(get("/api/mileage/team.csv")
                .header("Authorization", "Bearer valid-admin-token"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("'=HYPERLINK")));
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
mvn -pl addon-expenses-rest-api -Dtest=MileageConversionControllerTest test
```

Expected:
- FAIL because dangerous CSV values are not neutralized.

- [ ] **Step 3: Implement CSV sanitization**

In `MileageConversionController`, add:

```java
private static String spreadsheetSafe(String value) {
    if (value.isEmpty()) {
        return value;
    }
    char first = value.charAt(0);
    return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t'
            ? "'" + value
            : value;
}
```

Change `appendCsvRow` to:

```java
builder.append(escapeCsv(spreadsheetSafe(text(values[i]))));
```

- [ ] **Step 4: Run focused tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -Dtest=MileageConversionControllerTest test
```

Expected:
- PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/MileageConversionController.java \
  addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/api/MileageConversionControllerTest.java
git commit -m "fix: neutralize mileage csv formulas"
```

---

### Task 8: Bound Clockify Option Pagination

**Files:**
- Modify: `addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/clockify/ClockifyExpenseGateway.java`
- Modify: `addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/clockify/ClockifyExpenseGatewayTest.java`

- [ ] **Step 1: Write a failing pagination guard test**

Add:

```java
@Test
void listCategoriesStopsWhenClockifyKeepsReturningFullPages() throws Exception {
    when(expensesClient.getCategories(eq("ws-gateway"), any(ClockifyPageRequest.class)))
            .thenReturn(categoryPage(0, 200));

    assertThatThrownBy(() -> gateway.listCategories("ws-gateway"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Clockify pagination exceeded");
}
```

- [ ] **Step 2: Run the failing test**

Run:

```bash
mvn -pl addon-expenses-rest-api -Dtest=ClockifyExpenseGatewayTest#listCategoriesStopsWhenClockifyKeepsReturningFullPages test
```

Expected:
- FAIL or hang if run without a timeout. If it hangs, stop the test and implement the guard.

- [ ] **Step 3: Implement a bounded page guard**

In `ClockifyExpenseGateway`, add:

```java
private static final int MAX_OPTION_PAGES = 100;
```

Update `listAllPages`:

```java
while (page <= MAX_OPTION_PAGES) {
    int sourceCount = appender.append(page, out);
    if (sourceCount < pageSize) {
        return out;
    }
    page++;
}
throw new IllegalStateException("Clockify pagination exceeded " + MAX_OPTION_PAGES + " pages");
```

- [ ] **Step 4: Run focused gateway tests**

Run:

```bash
mvn -pl addon-expenses-rest-api -Dtest=ClockifyExpenseGatewayTest test
```

Expected:
- PASS.

- [ ] **Step 5: Commit**

Run:

```bash
git add addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/clockify/ClockifyExpenseGateway.java \
  addon-expenses-rest-api/src/test/java/com/cake/clockify/addon/mileage/clockify/ClockifyExpenseGatewayTest.java
git commit -m "fix: bound mileage option pagination"
```

---

### Task 9: Harden Vanilla UI Failure States

**Files:**
- Modify: `addon-expenses-rest-api/src/main/resources/static/assets/mileage/settings.js`
- Modify tests only if an existing static-JS test pattern is present; otherwise rely on source review plus full Maven verification.

- [ ] **Step 1: Decouple settings load from category load**

Replace `loadSettings()` with this shape:

```javascript
function loadSettings() {
  if (!element("settings-form")) {
    return Promise.resolve();
  }
  const settingsPromise = apiFetch("/api/mileage/settings");
  const categoriesPromise = loadCategories().catch(error => {
    toast("Mileage categories could not be loaded: " + error.message, "error");
  });
  return settingsPromise
    .then(settings => {
      element("settings-enabled").checked = settings.enabled;
      element("settings-rate").value = settings.rate || "";
      return categoriesPromise.then(() => {
        element("settings-mileage-category").value = settings.mileageCategoryId || settings.inputCategoryId || settings.outputCategoryId || "";
        element("settings-convert-create").checked = settings.convertOnCreate;
        element("settings-convert-update").checked = settings.convertOnUpdate;
        element("settings-rate-override").checked = settings.allowUserRateOverride;
        element("settings-status").textContent = settings.completeForNativeConversion ? "Ready" : "Needs configuration";
      });
    })
    .catch(error => toast(error.message, "error"));
}
```

- [ ] **Step 2: Prevent blank custom-date queries**

Add:

```javascript
function validSelectedDateRange(scope) {
  const range = selectedDateRange(scope);
  if (!range) {
    return null;
  }
  if (!range.from || !range.to) {
    toast("Choose both From and To dates.", "error");
    return null;
  }
  return range;
}
```

Change `rangeQuery(scope)` to call `validSelectedDateRange(scope)` instead of `selectedDateRange(scope)`.

- [ ] **Step 3: Validate receipt before upload**

Add constants near the top:

```javascript
const maxReceiptBytes = 10 * 1024 * 1024;
const allowedReceiptTypes = new Set([
  "image/png",
  "image/jpeg",
  "image/gif",
  "image/webp",
  "image/heic",
  "application/pdf"
]);
```

Add:

```javascript
function validateReceipt(file) {
  if (!file) {
    return true;
  }
  if (file.size > maxReceiptBytes) {
    toast("Receipt file exceeds 10 MB.", "error");
    return false;
  }
  if (!allowedReceiptTypes.has(file.type || "")) {
    toast("Unsupported receipt file type.", "error");
    return false;
  }
  return true;
}
```

In `createMileage`, immediately after reading `file`:

```javascript
if (!validateReceipt(file)) {
  return;
}
```

- [ ] **Step 4: Guard invalid dates**

Change `formatDate(value)` to:

```javascript
function formatDate(value) {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  const options = { year: "numeric", month: "2-digit", day: "2-digit", hour: "numeric", minute: "2-digit" };
  const timezone = timezoneFromClaims();
  if (timezone) {
    options.timeZone = timezone;
  }
  try {
    return new Intl.DateTimeFormat("en-US", options).format(date);
  } catch (e) {
    return new Intl.DateTimeFormat("en-US", { year: "numeric", month: "2-digit", day: "2-digit", hour: "numeric", minute: "2-digit" }).format(date);
  }
}
```

- [ ] **Step 5: Disable category repair while busy**

Replace `setupMileageCategory()` with:

```javascript
function setupMileageCategory() {
  const button = element("btn-setup-mileage-category");
  if (button && button.disabled) {
    return;
  }
  if (button) {
    button.disabled = true;
    button.textContent = "Repairing...";
  }
  apiFetch("/api/mileage/settings/mileage-category", { method: "POST" })
    .then(settings => {
      toast("Mileage category is ready.");
      return loadCategories().then(() => {
        if (element("settings-mileage-category")) {
          element("settings-mileage-category").value = settings.mileageCategoryId || "";
        }
        loadSettings();
        loadCreateContext();
        loadDiagnostics();
      });
    })
    .catch(error => toast(error.message, "error"))
    .finally(() => {
      if (button) {
        button.disabled = false;
        button.textContent = "Create or Repair Mileage Category";
      }
    });
}
```

- [ ] **Step 6: Run static checks and Maven tests**

Run:

```bash
rg -n "field-task|options/tasks|append\\(\"userId\"|userId:" addon-expenses-rest-api/src/main/resources/static/assets/mileage/settings.js
mvn -pl addon-expenses-rest-api -am test
```

Expected:
- The `rg` command exits 1 with no output.
- Maven tests pass.

- [ ] **Step 7: Commit**

Run:

```bash
git add addon-expenses-rest-api/src/main/resources/static/assets/mileage/settings.js
git commit -m "fix: harden mileage iframe ui states"
```

---

### Task 10: Refresh CLAUDE.md With Durable Learnings

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Add only durable guidance**

Add a short section near `## Verification Expectations`:

```markdown
## Final Hardening Workflow

- For the final hardening plan, implement `docs/superpowers/plans/2026-05-26-mileage-final-hardening.md` task-by-task.
- After implementation, run an adversarial review over DB defaults, webhook 2xx resilience, audit idempotency, CSV export safety, pagination bounds, and iframe UI failure states.
- Do not push until the adversarial review is complete and the full verification commands pass.
```

- [ ] **Step 2: Run doc scan**

Run:

```bash
rg -n "clockify-expenses-api|Clockify Expenses API|com\\.cake\\.clockify\\.addon\\.expenses|temp_addon_expenses|temp-addon-expenses|API_TEST_|CLOCKIFY_API_KEY:-|test-suite\\.sh" \
  addon-expenses-rest-api/src/main addon-expenses-rest-api/src/test/resources addon-expenses-rest-api/pom.xml \
  -g '!**/target/**' -g '!addon-expenses-rest-api/src/main/resources/db/migration/V5__*' \
  -g '!addon-expenses-rest-api/src/main/resources/db/migration/V10__*' \
  -g '!addon-expenses-rest-api/src/main/resources/db/migration/V12__*' \
  -g '!addon-expenses-rest-api/MARKETPLACE_OCS/**'
```

Expected:
- Exit 1 with no output.

- [ ] **Step 3: Commit**

Run:

```bash
git add CLAUDE.md
git commit -m "docs: record final hardening workflow"
```

---

### Task 11: Full Verification

**Files:**
- No edits unless verification exposes a real defect.

- [ ] **Step 1: Run formatting and stale scans**

Run:

```bash
git diff --check
rg -n "\\b(double|Double|float|Float)\\b" addon-core addon-db clockify-rest-client addon-testkit addon-expenses-rest-api/src/main addon-expenses-rest-api/src/test \
  --glob '!**/target/**' --glob '!addon-expenses-rest-api/addon-java-sdk/**'
rg -n "clockify-expenses-api|Clockify Expenses API|com\\.cake\\.clockify\\.addon\\.expenses|temp_addon_expenses|temp-addon-expenses|API_TEST_|CLOCKIFY_API_KEY:-|test-suite\\.sh" \
  addon-expenses-rest-api/src/main addon-expenses-rest-api/src/test/resources addon-expenses-rest-api/pom.xml \
  -g '!**/target/**' -g '!addon-expenses-rest-api/src/main/resources/db/migration/V5__*' \
  -g '!addon-expenses-rest-api/src/main/resources/db/migration/V10__*' \
  -g '!addon-expenses-rest-api/src/main/resources/db/migration/V12__*' \
  -g '!addon-expenses-rest-api/MARKETPLACE_OCS/**'
```

Expected:
- `git diff --check` exits 0.
- Both `rg` scans exit 1 with no output.

- [ ] **Step 2: Run full Maven verification**

Run:

```bash
mvn -pl addon-expenses-rest-api -am clean test
```

Expected:
- Reactor `BUILD SUCCESS`.

- [ ] **Step 3: Run Docker build**

Run:

```bash
docker compose -f addon-expenses-rest-api/docker-compose.yml build
```

Expected:
- Build completes successfully.

- [ ] **Step 4: Run manifest probe**

Run:

```bash
docker compose -f addon-expenses-rest-api/docker-compose.yml -f <(printf 'services:\n  db:\n    ports: !reset []\n') up -d
curl -fsS http://localhost:8080/manifest
docker compose -f addon-expenses-rest-api/docker-compose.yml -f <(printf 'services:\n  db:\n    ports: !reset []\n') down
```

Expected:
- `curl` returns JSON with `"schemaVersion":"1.5"` and `"key":"mileage-for-clockify"`.
- Compose stack is stopped afterward.

- [ ] **Step 5: Run secret scan if available**

Run:

```bash
gitleaks detect --source . --no-git --redact --verbose
```

Expected:
- `no leaks found`.
- If `gitleaks` is not installed, record that explicitly in the final summary and do not invent a pass.

---

### Task 12: Adversarial Review Before Push

**Files:**
- Review changed files only, plus directly related callers/tests.

- [ ] **Step 1: Review changed diff for architecture axioms**

Run:

```bash
git status --short --branch
git diff origin/main...HEAD --stat
git diff origin/main...HEAD
```

Check:
- No `double`, `Double`, `float`, or `Float`.
- No new Clockify API host literals.
- No frontend installation tokens, crypto keys, or backend secrets.
- No request-supplied `userId` added to create DTO, multipart allowlist, iframe HTML, or JS payload.
- No task selector, task options endpoint, `taskId` create field, or `TASK_READ` scope.
- No new parallel expense reporting tables or duplicated Clockify-owned state.
- All new DB schema changes are in Flyway migrations.
- Webhook handler and event-service failures produce safe 2xx behavior where appropriate.
- Audit race fixes remain workspace-scoped.

- [ ] **Step 2: Review race/idempotency behavior**

Read these files and answer each item in the session notes:

```bash
sed -n '1,260p' addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/conversion/MileageConversionService.java
sed -n '1,240p' addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/api/MileageApiController.java
sed -n '1,180p' addon-expenses-rest-api/src/main/java/com/cake/clockify/addon/mileage/audit/MileageConversionReservationRepository.java
```

Questions:
- Can two webhook deliveries for one workspace/expense create more than one row?
- Can manual create fail locally after Clockify already created the expense because a webhook row won the race?
- Does every lookup include `workspaceId`?
- Does failure recording preserve admin visibility?

- [ ] **Step 3: Review UI changes**

Run:

```bash
sed -n '1,760p' addon-expenses-rest-api/src/main/resources/static/assets/mileage/settings.js
```

Check:
- Settings form can populate even if category options fail.
- Custom date range does not silently fall back to the default week when blank.
- Receipt type/size feedback happens before upload.
- Invalid dates do not throw.
- Mileage category repair button cannot send duplicate calls.

- [ ] **Step 4: Fix review findings before push**

If the review finds any actionable issue, implement the smallest fix, add or update a test, rerun the relevant focused test, and rerun Task 11.

- [ ] **Step 5: Push only after review is clean**

Run:

```bash
git status --short --branch
git log --oneline --decorate -8
git push origin main
```

Expected:
- Working tree is clean before push.
- Push succeeds.
- Final response states exact commands run, what passed, and any residual risk.

