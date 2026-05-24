# Mileage Edge Cases

- If mileage settings are incomplete, add-on-created expenses return `configuration_missing` and native conversions record `SKIPPED`.
- Duplicate webhook delivery is idempotent through the `(workspace_id, expense_id)` audit row and marker checks.
- If a marker is manually removed from Clockify notes, the audit row still prevents a second conversion after `CONVERTED`.
- Locked or finalized expenses are skipped with `FINALIZED_OR_LOCKED`.
- Delete webhooks mark the audit row `DELETED`; they never hard-delete the row.
- Restored expenses with an existing converted audit row or marker become `RESTORED_IGNORED`.
- Receipt-bearing add-on-created expenses forward the receipt to Clockify. Native conversion updates category, amount, and notes on the same expense so receipt preservation depends on Clockify retaining files during those field updates.
- Clockify API failures are stored with sanitized status-only failure messages.
