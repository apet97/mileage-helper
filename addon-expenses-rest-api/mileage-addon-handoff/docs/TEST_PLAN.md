# Test Plan

## 1. Unit tests

### MileageCalculator

Cases:
- `37.4 * 0.655 = 24.4970 -> 24.50`
- `1 * 0.655 = 0.655 -> 0.66`
- `10 * 0.655 = 6.550 -> 6.55`
- zero miles rejected
- negative miles rejected
- zero rate rejected
- invalid scale accepted then rounded
- `HALF_UP`, `HALF_EVEN`, `DOWN` behavior

### MileageNoteService

- Appends marker.
- Preserves original note.
- Handles blank note.
- Does not duplicate marker.
- Escapes/sanitizes unsafe text if rendered in UI.

### MileageEligibilityService

- Disabled settings -> skipped.
- Missing output category -> skipped.
- Non-input category -> skipped.
- Output category -> ignored.
- Existing marker -> ignored.
- Existing conversion -> ignored.
- Missing quantity -> skipped.
- Locked/finalized -> skipped.

## 2. Repository tests

Use Testcontainers PostgreSQL.

- Save settings.
- Update settings.
- Save conversion.
- Unique constraint prevents duplicate conversion.
- Mark deleted.
- Query recent conversions per workspace.
- Workspace isolation.

## 3. Clockify API gateway tests

Use WireMock/MockWebServer.

- Fetch expense success.
- Fetch expense 404.
- Update expense success.
- Update expense 409 locked.
- Create expense with JSON.
- Create expense multipart with receipt.
- Category list normalization.
- Retry-after handling for 429 if implemented.

## 4. Webhook handler tests

- Invalid signature rejected or recorded according to framework behavior.
- `EXPENSE_CREATED` full payload converts.
- `EXPENSE_CREATED` ref-only payload fetches full expense.
- `EXPENSE_UPDATED` after conversion is ignored.
- `EXPENSE_UPDATED` input category unconverted converts.
- `EXPENSE_DELETED` marks conversion deleted.
- `EXPENSE_RESTORED` already converted ignored.
- Cross-workspace mismatch skipped.

## 5. Integration tests

### Add-on-created expense

1. Configure settings.
2. Submit mileage create request.
3. Assert generated Clockify API request:
   - category = output
   - amount = rounded
   - note contains formula and marker
4. Assert conversion row.

### Native conversion

1. Configure settings.
2. Simulate `EXPENSE_CREATED`.
3. Mock full expense response with input category and quantity.
4. Assert update request to Clockify.
5. Simulate resulting `EXPENSE_UPDATED`.
6. Assert no second update.

### Receipt preservation

1. Simulate native expense with file metadata.
2. Update expense.
3. Verify update does not remove receipt fields if API requires preserving them.
4. If receipt preservation is automatic, assert no file deletion call is made.

## 6. Manifest validation test

Fetch or load official schema 1.5, then validate `/manifest`.

Expected:
- no schema validation errors
- webhooks include expense events
- minimum subscription plan is PRO
- scopes are unique

## 7. Manual test checklist

In Clockify developer workspace:

1. Install add-on.
2. Open Mileage sidebar.
3. Configure rate and categories.
4. Create mileage via add-on.
5. Verify real Clockify expense appears.
6. Upload receipt via add-on.
7. Verify receipt appears in Clockify.
8. Create native mileage expense on web.
9. Verify conversion.
10. Create native mileage expense on mobile.
11. Verify conversion.
12. Confirm `EXPENSE_UPDATED` fires and is ignored.
13. Delete converted expense.
14. Verify audit row marked deleted.
15. Restore expense.
16. Verify no duplicate conversion.
17. Approve/finalize an expense, then attempt conversion.
18. Verify skip/failure logged.
19. Disable add-on.
20. Verify webhooks no-op.

## 8. Regression tests

- Category archived/deleted.
- Rate changed after previous conversions.
- Existing converted expense updated by user.
- User removes marker manually.
- Multiple webhooks arrive concurrently.
- Duplicate webhook delivery.
- Out-of-order update before create.
- Workspace uninstall cleanup.
