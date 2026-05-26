# Mileage Edge Cases

- If mileage settings are incomplete, add-on-created expenses return `configuration_missing` and native conversions record `SKIPPED`.
- The create page loads `/api/mileage/create-context` before enabling Preview/Create. If the context cannot load, the buttons stay disabled.
- Manual add-on-created expenses default to billable when `billable` is omitted. An explicit `billable=false` remains non-billable.
- Rate override input is hidden and omitted unless workspace settings allow user overrides. Backend calculation still ignores submitted override rates when override is disabled.
- Unit and rounding settings are fixed to `mile` and `HALF_UP`; legacy input/output categories normalize to the single Mileage category.
- If a Clockify workspace already has a usable `Mileage` UNIT/mile category with a positive unit price, setup can adopt it and derive the add-on rate from cents instead of creating a duplicate category.
- Duplicate webhook delivery is idempotent through the `(workspace_id, expense_id)` audit row and marker checks.
- Expense webhooks can arrive as full payloads with `id` or reference payloads with `expenseId`; missing both IDs is a no-op.
- If a marker is manually removed from Clockify notes, the audit row still prevents a second conversion after `CONVERTED`.
- Locked or finalized expenses are skipped with `FINALIZED_OR_LOCKED`.
- Delete webhooks mark the audit row `DELETED`; they never hard-delete the row. `Mine` and `Team` views hide deleted rows, while the admin Conversions view keeps them for audit.
- Restored expenses with an existing converted audit row or marker become `RESTORED_IGNORED`.
- Receipt-bearing add-on-created expenses forward the receipt to Clockify. Native conversion updates amount and notes on the same expense so receipt preservation depends on Clockify retaining files during those field updates.
- Clockify API failures are stored with sanitized status-only failure messages.
