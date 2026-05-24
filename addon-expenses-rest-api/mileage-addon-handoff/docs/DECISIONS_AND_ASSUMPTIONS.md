# Decisions and Assumptions

## Decisions

1. Clockify remains source of truth for expenses.
2. Add-on DB stores settings and conversion audit only.
3. Manifest targets schema 1.5.
4. Minimum plan is PRO.
5. Add-on UI is a sidebar component accessible to everyone.
6. Admin settings are restricted inside UI/API.
7. Native/mobile compatibility is handled through expense webhooks.
8. All calculations use BigDecimal.
9. Converted expense notes contain a machine-readable marker.
10. `EXPENSE_UPDATED` is a repair path and loop-prevention path, not the primary conversion trigger.

## Assumptions to verify

1. Verified: the implemented repo uses a manual schema `1.5` manifest via `MileageManifestV15`; do not switch to an SDK `v1_5Builder()` unless verified locally.
2. Verified by tests/live evidence: the Clockify expense gateway supports create/update and receipt paths needed by the add-on.
3. Verified by tests/live evidence: full expense fetch can provide quantity/miles for the implemented conversion path.
4. Partially verified: category normalization supports unit vs flat hints where available; admin diagnostics still need real workspace category checks before release.
5. Verified in the sacrificial workspace during implementation: multipart update preserving original user/date/billable context retained receipt state. Rerun before release if Clockify behavior may have drifted.
6. Partially verified: locked/finalized state is skipped when visible and sanitized failures are recorded on upstream conflict/error.
7. Verified in tests: admin-only API paths use normalized claims and reject non-admin roles.
