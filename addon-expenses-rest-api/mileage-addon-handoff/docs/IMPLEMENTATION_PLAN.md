# Implementation Plan

Status: historical. The staged implementation described here has been completed in the standalone repository. Use it for audit context, not as the primary task list for future agents. Current active guidance lives in the repo root `AGENTS.md`, `CLAUDE.md`, and `addon-expenses-rest-api/README.md`.

## Phase 0 — Repository setup and SDK inspection

1. Confirm Java version.
2. Confirm Maven configuration for official `com.cake.clockify:addon-sdk`.
3. Inspect installed SDK version:
   - Does it expose `ClockifyManifest.v1_5Builder()`?
   - Does it include schema 1.5 events?
   - Does it provide helpers for lifecycle/webhook token verification?
4. Confirm generated OpenAPI expense client compiles.
5. Confirm application starts locally.

Deliverable:
- Project builds.
- `/health` or equivalent returns OK.
- Decision recorded: SDK-native schema 1.5 builder or manual schema 1.5 manifest JSON.

## Phase 1 — Rename and productize boilerplate

1. Rename package/module/user-facing labels from generic Expenses API to Mileage.
2. Change app name to `Mileage for Clockify`.
3. Replace placeholder settings:
   - remove `enableNotifications`
   - remove `maxExpenseAmount`
   - add mileage settings.
4. Change manifest minimum subscription plan to `PRO`.
5. Add/confirm schemaVersion `1.5`.
6. Add scopes:
   - `EXPENSE_READ`
   - `EXPENSE_WRITE`
   - `USER_READ`
   - `PROJECT_READ`
   - optional `WORKSPACE_READ`.

Deliverable:
- Manifest describes Mileage add-on, not generic expense API.
- Manifest validates against official schema 1.5.

## Phase 2 — Database schema

1. Add Flyway/Liquibase if not present.
2. Create `mileage_workspace_settings`.
3. Create `mileage_conversion`.
4. Optional: create `mileage_webhook_event_log`.
5. Add JPA entities and repositories.
6. Add unique constraints.

Deliverable:
- Migrations run successfully.
- Repositories have unit tests.

## Phase 3 — Settings and admin UI

1. Implement `MileageSettingsService`.
2. Implement category options endpoint.
3. Build custom settings UI:
   - enabled
   - rate
   - unit
   - input category selector
   - output category selector
   - rounding mode
   - conversion toggles
   - dry-run mode
4. Add diagnostics:
   - install token available
   - API reachable
   - output category valid
   - input category valid
   - webhooks registered
5. Hide admin settings from non-admin users.

Deliverable:
- Admin can configure add-on without editing IDs manually when category list API works.
- Settings persist and reload.

## Phase 4 — Calculation service

1. Implement `MileageCalculator`.
2. Use `BigDecimal`.
3. Validate miles/rate.
4. Implement rounding modes.
5. Unit test edge cases:
   - 37.4 × 0.655 = 24.497 -> 24.50 with HALF_UP
   - zero miles rejected
   - negative miles rejected
   - invalid rate rejected
   - high precision accepted

Deliverable:
- Calculation tests pass.

## Phase 5 — Add-on-created expense flow

1. Implement request DTOs:
   - `CreateMileageExpenseRequest`
   - `MileagePreviewRequest`
2. Implement `POST /api/mileage/preview`.
3. Implement `POST /api/mileage/expenses` JSON.
4. Implement `POST /api/mileage/expenses` multipart.
5. Create real Clockify flat expense:
   - category = output category
   - amount = rounded calculated amount
   - notes = template + marker
   - preserve selected user/project/task/date/billable
6. Attach receipt if supplied.
7. Store conversion record with source `ADDON_FORM`.

Deliverable:
- User can create a real Clockify expense from Mileage UI.
- Receipt upload works.
- Audit row exists.

## Phase 6 — Native/mobile conversion service

1. Implement `ClockifyExpenseGateway`.
2. Implement `MileageConversionService.convertIfEligible(workspaceId, expenseId, sourceEventType)`.
3. Implement eligibility checks:
   - enabled
   - settings complete
   - input category match
   - not output category
   - no note marker
   - no successful conversion
   - valid quantity
   - not finalized/locked where detectable
4. Implement update payload:
   - categoryId = output category
   - amount = rounded amount
   - notes = appended note + marker
   - preserve date/project/task/user/billable
5. Implement transaction/idempotency.

Deliverable:
- Given an eligible unit mileage expense ID, service converts it once.

## Phase 7 — Webhook handlers

1. Update `EXPENSE_CREATED` handler to call conversion service.
2. Update `EXPENSE_UPDATED` handler to call conversion service as repair path.
3. Update `EXPENSE_DELETED` handler to mark conversion as deleted.
4. Update `EXPENSE_RESTORED` handler to recheck eligibility.
5. Add webhook verification tests.
6. Add loop prevention tests.

Deliverable:
- Native Clockify mobile-created mileage expense converts automatically.
- Self-triggered `EXPENSE_UPDATED` is ignored.

## Phase 8 — Conversion log UI

1. Implement admin conversion list.
2. Show statuses:
   - converted
   - skipped
   - failed
   - dry-run
   - deleted
3. Add detail modal:
   - expense ID
   - miles
   - rate
   - amount
   - source
   - skip/error reason
4. Add retry button for failed/skipped safe cases.

Deliverable:
- Admin can see and diagnose conversions.

## Phase 9 — Security hardening

1. Verify all tokens.
2. Never expose installation token to frontend.
3. Redact tokens from logs.
4. Validate file content type/size.
5. Add CSRF/referer strategy appropriate for iframe.
6. Add strict response headers.
7. Add rate limiting if needed.
8. Ensure workspace isolation in every query.

Deliverable:
- Security checklist passes.

## Phase 10 — Test and release

1. Unit tests.
2. Integration tests with mocked Clockify API.
3. Testcontainers DB tests.
4. Manual Clockify developer workspace tests:
   - install
   - configure
   - add-on create
   - native mobile create
   - receipt upload/preserve
   - update loop
   - delete/restore
5. Validate `/manifest` against official schema 1.5 endpoint.
6. Prepare marketplace/private listing info.

Deliverable:
- Release candidate ready for private install.
