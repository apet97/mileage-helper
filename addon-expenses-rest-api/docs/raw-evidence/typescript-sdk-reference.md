# Expenses

Workspace-scoped monetary entries tied to projects, categories, and optionally time entries. Requires the `EXPENSES` workspace feature.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/v1/workspaces/{wsId}/expenses` | List |
| GET | `/v1/workspaces/{wsId}/expenses/{id}` | Get one |
| POST | `/v1/workspaces/{wsId}/expenses` | Create |
| PUT | `/v1/workspaces/{wsId}/expenses/{id}` | Update |
| DELETE | `/v1/workspaces/{wsId}/expenses/{id}` | Delete |
| GET | `/v1/workspaces/{wsId}/expenses/categories` | List categories |

## Required scopes

- Read: `EXPENSE_READ`
- Write: `EXPENSE_WRITE`

## Sample payload — `expenses.sample.json`

```json
{
  "id": "1111111111111111111a0090",
  "workspaceId": "1111111111111111111a0002",
  "categoryId": "1111111111111111111a0091",
  "projectId": "1111111111111111111a000a",
  "userId": "1111111111111111111a0041",
  "billable": true,
  "date": "2026-04-15",
  "notes": "Sample description",
  "total": 10000,
  "quantity": 1,
  "fileId": null
}
```

(Real API may wrap list responses in `{ expenses: [...], count: ... }` — the SDK normalises.)

## Fields worth knowing

- `categoryId` — required on create. Fetch categories first or cache them.
- `total` — integer in minor units (cents / euro cents / etc.). Divide by 100 when displaying.
- `quantity` — defaults to 1; used for per-unit expense categories.
- `fileId` — attached receipt file if uploaded.
- `billable` — distinct from project billability; an expense can be non-billable on a billable project.

## Webhook events

- `EXPENSE_CREATED` → payload uses `id`.
- `EXPENSE_RESTORED` → payload uses `id`.
- `EXPENSE_UPDATED` → payload uses `expenseId` (different key!).
- `EXPENSE_DELETED` → payload uses `expenseId`.

Handle this ID-field divergence when writing `switch` statements over expense events.

## SDK helpers

- `client.getExpenses(workspaceId, params)` — returns the live double-nested list wrapper `{ expenses: { count, expenses }, dailyTotals, weeklyTotals }`.
- `client.getExpense(workspaceId, expenseId)` — single expense read.
- `client.createExpense(workspaceId, body)` — multipart create; requires `userId`, `amount`, `date`, and `categoryId`.
- `client.updateExpense(workspaceId, expenseId, body)` — multipart update; `changeFields` must list which fields apply.
- `client.deleteExpense(workspaceId, expenseId)` — delete an expense.
- `client.getExpenseCategories(...)`, `createExpenseCategory(...)`, `updateExpenseCategory(...)`, `deleteExpenseCategory(...)`.
- `createReportService(ctx).expensesAndTotals(req)` — expense detailed report.
- `paginate(fetchPage)` for listing.

## Gotchas

- Workspaces without the `EXPENSES` feature return 404 on all expense endpoints. Check `features: [...]` before calling.
- Create/update are `multipart/form-data`, not JSON. Let the SDK build the `FormData`; do not set `Content-Type` manually.
- `amount` is sent as the raw upstream form value. Probe-lab observed `amount=100` returning `total=10000`, so display/currency scaling should be handled deliberately at the UI boundary.
- Category deletion is blocked if any expense still references the category.
- `total` precision: cents-style integers. NEVER treat as floats — rounding drift corrupts financial data.

## Related

- `docs/reference/reports.md` (`expensesAndTotals`)
- `docs/webhook-events.md`
