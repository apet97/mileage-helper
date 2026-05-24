# Mileage Webhooks

The manifest subscribes to:

- `EXPENSE_CREATED`
- `EXPENSE_UPDATED`
- `EXPENSE_DELETED`
- `EXPENSE_RESTORED`

Created and restored payloads carry `id`; updated and deleted payloads carry `expenseId`. Handlers return without throwing when a payload is empty or the expense ID is missing.

Native/mobile unit mileage conversion is performed by fetching the current expense through `ClockifyExpenseGateway`, checking workspace isolation, eligibility, marker state, and audit state, then updating the same Clockify expense to the configured flat output category.

Loop prevention is layered:

- Existing `CONVERTED` or `CONVERTING` audit rows skip duplicate work.
- Output-category expenses are skipped.
- Notes containing `[MileageAddon:converted:v1 ...]` are skipped.
- Core webhook dedupe prevents repeated delivery processing.
