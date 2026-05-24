# Migration from Current Expense API Boilerplate

Status: completed. This document is retained as a migration record from the original generic expense API boilerplate to the current Mileage for Clockify implementation. For current architecture and commands, use the repo root docs and source/tests.

## 1. Current boilerplate strengths

Keep these pieces:

- Expense CRUD controller.
- Multipart create/update with receipt support.
- Category CRUD controller.
- Exception handler with sanitized Clockify API errors.
- Webhook handler structure.
- Settings iframe controller structure.
- Lifecycle handler structure.
- Clockify client factory.
- Repository pattern.

## 2. Rename product surface

Rename:
- `ClockifyExpensesApiAddonApplication` -> `MileageAddonApplication`
- `ClockifyExpensesApiManifestConfig` -> `MileageManifestConfig`
- `SettingsIframeController` -> `MileageIframeController`
- UI text from the old generic expenses API product label to "Mileage for Clockify"

## 3. Replace placeholder settings

Remove:
- `enableNotifications`
- `maxExpenseAmount`

Add:
- `mileage.enabled`
- `mileage.rate`
- `mileage.unit`
- `mileage.inputCategoryId`
- `mileage.outputCategoryId`
- `mileage.roundingMode`
- `mileage.convertOnCreate`
- `mileage.convertOnUpdate`
- `mileage.preserveOriginalNotes`
- `mileage.dryRunMode`
- `mileage.allowUserRateOverride`

## 4. Change manifest

Current generic manifest likely uses:
- free plan
- generic description
- admin-only sidebar

Change to:
- schema 1.5
- PRO plan
- Mileage name/description
- scopes for expense read/write, user read, project read
- sidebar accessible to everyone if users create mileage via add-on UI
- custom admin settings UI or admin tab inside iframe

## 5. Replace temp log table

Current table is a temporary event log.

Replace or add:
- `mileage_workspace_settings`
- `mileage_conversion`
- optional `mileage_webhook_event_log`

Use `BigDecimal` instead of `Double`.

## 6. Convert logging service to conversion service

Current service logs payloads.

Create:
- `MileageConversionService`
- `MileageCalculator`
- `MileageEligibilityService`
- `MileageNoteService`
- `ClockifyExpenseGateway`

Existing `ExpenseLogService.logExpenseFromFetched(...)` pattern is good because update webhooks may be reference-only; keep that fetch-first approach.

## 7. Update webhook handlers

### ExpenseCreatedHandler

Before:
```text
log payload
```

After:
```text
conversionService.convertIfEligible(claims, expenseId, "EXPENSE_CREATED")
```

### ExpenseUpdatedHandler

Before:
```text
fetch and log
```

After:
```text
conversionService.convertIfEligible(claims, expenseId, "EXPENSE_UPDATED")
```

But it must ignore:
- output category
- marker
- existing conversion

### ExpenseDeletedHandler

Before:
```text
delete log row
```

After:
```text
mark conversion status DELETED
```

Do not hard-delete audit.

### ExpenseRestoredHandler

Before:
```text
log payload
```

After:
```text
conversionService.recheckRestored(...)
```

## 8. Keep generic expense endpoints?

Yes, but avoid exposing a generic expense manager UX.

Recommended:
- Keep generic `ExpenseController` as backend helper if needed.
- Add purpose-specific `MileageApiController`.
- Do not make the UI look like a generic Expenses API explorer.

## 9. UI migration

Current tabs:
- Dashboard
- Configuration
- Connection Test

New tabs:
- Create Mileage
- My Mileage
- Admin Settings
- Conversion Log
- Diagnostics

Admin-only:
- settings
- conversion log
- diagnostics

Everyone:
- create mileage
- personal recent submissions

## 10. Critical code review points

- No `Double` for mileage/money.
- No update loop.
- No installation token frontend exposure.
- No hardcoded API URLs.
- No hard-delete of audit on expense delete.
- Manifest validates as 1.5.
