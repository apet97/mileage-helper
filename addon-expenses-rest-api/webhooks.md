# Mileage Webhooks

The manifest subscribes to:

- `EXPENSE_CREATED`
- `EXPENSE_UPDATED`
- `EXPENSE_DELETED`
- `EXPENSE_RESTORED`

Webhook payloads may identify the expense as either `id` on a full expense payload or `expenseId` on a reference payload. Handlers use the effective expense ID from either field. Handlers return without throwing when a payload is empty or both IDs are missing.

Created, restored, and updated events that have an effective expense ID fetch the current expense through `ClockifyExpenseGateway`, check workspace isolation, eligibility, workflow preflight, marker state, and audit state, then update the same Clockify expense with the rounded amount and clean exact note when eligible.

Native/mobile conversion resolves the active mileage rate by the Clockify expense date and stores `rateSource`, `ratePolicyId`, and `ratePolicyName` on the audit row. Manual-only trip evidence fields remain blank for webhook-created rows; the webhook path does not infer origin/destination, odometer values, or policy exception text from Clockify.

Workflow preflight treats locked/finalized expenses as blockers (`FINALIZED_OR_LOCKED`). Approval and invoicing state are surfaced as warnings on the audit response/UI, not conversion blockers.

Workspace settings `convertOnCreate` and `convertOnUpdate` are enforced before fetching the Clockify expense; disabled event types are recorded as skipped with `EVENT_DISABLED`.

Deleted events mark the audit row `DELETED` and set `deletedAt`; the row is retained for admin audit history. `Mine` and `Team` views/CSVs exclude deleted rows.

Loop prevention is layered:

- Existing `CONVERTED` or `CONVERTING` audit rows skip duplicate work, even when the visible Clockify note has no marker.
- Notes containing `[MileageAddon:converted:v1 ...]` are skipped.
- Core webhook dedupe prevents repeated delivery processing.
- Webhook handlers acknowledge safely with HTTP 2xx after internal failure recording/logging so Clockify does not blindly retry failures that must be handled through diagnostics or admin retry.
