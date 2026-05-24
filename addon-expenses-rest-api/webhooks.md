# Mileage Webhooks

The manifest subscribes to:

- `EXPENSE_CREATED`
- `EXPENSE_UPDATED`
- `EXPENSE_DELETED`
- `EXPENSE_RESTORED`

Created and restored payloads usually carry `id`; handlers also accept `expenseId` reference payloads. Updated and deleted payloads carry `expenseId`. Handlers return without throwing when a payload is empty or the expense ID is missing, but any present ID must be used to fetch the current Clockify expense before eligibility checks.

Native/mobile Mileage-category conversion is performed by fetching the current expense through `ClockifyExpenseGateway`, checking workspace isolation, eligibility, marker state, and audit state, then updating the same Clockify expense with the rounded amount and clean exact note.

Loop prevention is layered:

- Existing `CONVERTED` or `CONVERTING` audit rows skip duplicate work, even when the visible Clockify note has no marker.
- Notes containing `[MileageAddon:converted:v1 ...]` are skipped.
- Core webhook dedupe prevents repeated delivery processing.
