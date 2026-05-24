# Implementation Evidence Checklist

This checklist reflects the implemented standalone repository. It is not a task queue for rebuilding from boilerplate.

## SDK / Manifest

- [x] Official `addon-sdk` dependency resolves from the vendored Maven repo.
- [x] Manual schema `1.5` manifest strategy is implemented in `MileageManifestV15`.
- [x] Manifest validation test covers schema `1.5`.
- [x] Minimum plan is `PRO`.
- [x] Required scopes are configured.
- [x] Lifecycle endpoints are configured.
- [x] Expense webhooks are configured.
- [x] Sidebar component is configured at `/iframe/mileage`.

## Settings

- [x] Settings entity and repository exist.
- [x] Settings service exists.
- [x] Admin settings API exists.
- [x] Category selector endpoint exists.
- [x] Diagnostics endpoint exists.
- [x] Settings UI is served by `/iframe/settings`.

## Calculation

- [x] `MileageCalculator` exists.
- [x] Mileage/rate/money calculations use `BigDecimal`.
- [x] Rounding mode support is tested.
- [x] Calculation unit tests cover the 37.4 * 0.655 case.

## Add-on Form

- [x] Preview endpoint exists.
- [x] JSON create mileage endpoint exists.
- [x] Multipart create mileage endpoint exists.
- [x] Receipt upload is bounded and content-type checked.
- [x] Conversion audit row is written for add-on-created expenses.

## Conversion

- [x] `ClockifyExpenseGateway` exists.
- [x] Eligibility service exists.
- [x] Note marker service exists.
- [x] Conversion service exists.
- [x] Idempotency constraint exists on `(workspace_id, expense_id)`.
- [x] Loop prevention is implemented through audit state, category guard, and note marker.

## Webhooks

- [x] Created handler delegates to conversion.
- [x] Updated handler is loop-safe repair path.
- [x] Deleted handler marks audit rows deleted.
- [x] Restored handler rechecks eligibility.
- [x] Core webhook signature plumbing is covered by platform tests.
- [x] Expense webhook integration tests cover create/update/delete/restore behavior.

## UI

- [x] Mileage UI route exists.
- [x] Admin settings route exists.
- [x] Admin-only APIs enforce admin role.
- [x] Frontend strips auth token from URL.
- [x] Inline script/style/event-handler regressions are covered by security tests.

## Tests and Verification

- [x] Unit tests.
- [x] Repository tests.
- [x] API gateway tests.
- [x] Webhook tests.
- [x] Manifest validation test.
- [x] Integration tests.
- [x] Docker image build has been verified from the standalone repo.
- [ ] Manual developer workspace validation should be rerun before release if Clockify behavior may have drifted.

## Security

- [x] Token redaction and no-token-frontend checks.
- [x] Workspace isolation checks in repositories/controllers.
- [x] Safe receipt handling.
- [x] Sanitized error responses.
- [x] No committed installation tokens.
