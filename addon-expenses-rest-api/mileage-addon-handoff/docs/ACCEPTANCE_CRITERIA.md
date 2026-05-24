# Acceptance Criteria

Status: release checklist. Most implementation items are covered by current tests and the evidence checklist in `../agent/IMPLEMENTATION_CHECKLIST.md`; manual developer workspace validation should still be rerun before release because Clockify API behavior can drift.

## Manifest and installation

- [ ] `/manifest` returns valid JSON.
- [ ] Manifest validates against official Clockify manifest schema `1.5`.
- [ ] Manifest includes `schemaVersion: "1.5"`.
- [ ] Manifest uses `minimalSubscriptionPlan: "PRO"`.
- [ ] Manifest includes `EXPENSE_READ` and `EXPENSE_WRITE`.
- [ ] Manifest registers `EXPENSE_CREATED`, `EXPENSE_UPDATED`, `EXPENSE_DELETED`, and `EXPENSE_RESTORED`.
- [ ] Manifest exposes a Mileage sidebar component.
- [ ] Add-on installs in Clockify developer/test workspace.
- [ ] Install lifecycle stores required installation context securely.

## Settings

- [ ] Admin can enable/disable Mileage.
- [ ] Admin can configure mileage rate with 3+ decimal places.
- [ ] Admin can configure mileage unit.
- [ ] Admin can select or enter input unit mileage category.
- [ ] Admin can select or enter output flat mileage category.
- [ ] Admin can configure rounding mode.
- [ ] Admin can enable/disable native conversion.
- [ ] Admin can enable dry-run mode.
- [ ] Non-admin users cannot change settings.
- [ ] Missing required settings produce a clear diagnostics warning.

## Calculation

- [ ] Calculation uses `BigDecimal`.
- [ ] No money/mileage/rate domain field uses `Double`, `double`, `Float`, or `float`.
- [ ] `37.4 × 0.655` produces calculated amount `24.4970` and rounded amount `24.50` with `HALF_UP`.
- [ ] Zero miles rejected.
- [ ] Negative miles rejected.
- [ ] Non-numeric miles/rate rejected.
- [ ] Rounding modes are unit tested.

## Add-on-created expense

- [ ] User can submit mileage through add-on UI.
- [ ] Add-on creates a real Clockify flat expense.
- [ ] Created expense uses configured output flat category.
- [ ] Created expense amount equals rounded calculated amount.
- [ ] Created expense note includes human-readable formula.
- [ ] Created expense note includes machine-readable marker.
- [ ] Created expense preserves selected date/project/task/user/billable fields.
- [ ] Receipt upload attaches to the real Clockify expense.
- [ ] Conversion audit row is created with source `ADDON_FORM`.

## Native/mobile conversion

- [ ] Native Clockify expense in configured input category triggers conversion on `EXPENSE_CREATED`.
- [ ] Add-on fetches full expense before converting.
- [ ] Add-on converts same expense to configured output flat category.
- [ ] Add-on writes rounded amount as flat expense amount.
- [ ] Original receipt remains attached after conversion.
- [ ] Original note is preserved when `preserveOriginalNotes` is true.
- [ ] Conversion audit row is created with source `WEBHOOK_CREATED`.
- [ ] If expense is not in input category, it is ignored/skipped.
- [ ] If expense is already in output category, it is ignored.
- [ ] If expense already has marker, it is ignored.
- [ ] If conversion already exists, it is ignored.

## Webhook loop prevention

- [ ] Conversion update triggers `EXPENSE_UPDATED`.
- [ ] `EXPENSE_UPDATED` handler fetches expense and detects output category/marker.
- [ ] Handler does not update the expense again.
- [ ] No repeated conversion loop occurs.
- [ ] Integration test simulates create -> update webhook sequence.

## Deleted/restored behavior

- [ ] `EXPENSE_DELETED` marks audit row as `DELETED`.
- [ ] Deleted audit row is not hard-deleted unless workspace is uninstalled.
- [ ] `EXPENSE_RESTORED` rechecks eligibility.
- [ ] Restored already-converted expense is not converted again.
- [ ] Restored input-category unconverted expense is converted if eligible.

## Finalized/locked records

- [ ] If API indicates approved/locked/invoiced/finalized state, add-on skips conversion.
- [ ] If update API returns a conflict/locked response, add-on records sanitized failure.
- [ ] Failed/skipped status is visible in admin log.

## Security

- [ ] Webhook signatures are verified.
- [ ] Lifecycle signatures are verified.
- [ ] User tokens are verified for iframe API.
- [ ] Installation token is never sent to frontend.
- [ ] Tokens are redacted from logs.
- [ ] Workspace ID from claims is enforced on every DB query.
- [ ] Clockify API URLs are taken from token/installation context, not hardcoded.
- [ ] File uploads validate type/size.
- [ ] Response errors do not expose stack traces or tokens.

## UI

- [ ] Mileage page loads inside Clockify iframe.
- [ ] Users can preview calculation before submit.
- [ ] Users can submit mileage expense.
- [ ] Admin sees settings/diagnostics/log tabs.
- [ ] Non-admin users do not see admin controls.
- [ ] Admin can view recent conversions.
- [ ] Admin can view failure reasons.
- [ ] Admin can retry safe failed conversions.

## Testing and CI

- [ ] Unit tests pass.
- [ ] Integration tests pass.
- [ ] Manifest validation test passes.
- [ ] CI runs on pull requests.
- [ ] Docker image builds.
- [ ] Manual developer workspace test checklist completed.
