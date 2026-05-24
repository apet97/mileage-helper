# Architecture

## 1. High-level architecture

```text
Clockify Web UI
  └─ iframe: Mileage add-on UI
       └─ Add-on backend
            ├─ Official Clockify addon-java-sdk integration
            ├─ Local clockify-rest-client expense gateway
            ├─ MileageConversionService
            ├─ Workspace settings store
            └─ Mileage conversion audit DB

Clockify Mobile/Web Native Expenses
  └─ EXPENSE_CREATED / EXPENSE_UPDATED / EXPENSE_DELETED / EXPENSE_RESTORED webhooks
       └─ Add-on backend webhook handlers
            └─ MileageConversionService
                 └─ Clockify expense update through ClockifyExpenseGateway
```

## 2. Source of truth

```text
Clockify Expense = official financial record
Mileage add-on DB = configuration + conversion metadata/audit only
```

The add-on must not become a parallel expense reporting system.

## 3. Main components

### Manifest provider

Returns schema 1.5 manifest. Use official Java SDK where possible. If SDK builder lacks schema 1.5 support, serve final JSON manually while still using SDK for supported add-on utilities.

### Lifecycle handler

Handles installation, deletion, status change, and settings updates.

Responsibilities:
- Store installation token securely.
- Store environment-specific API URLs from token claims.
- Clean workspace records on uninstall.
- Disable processing on inactive status.

### Webhook controller / handlers

Receives and verifies webhook requests.

Handlers:
- `ExpenseCreatedWebhookHandler`
- `ExpenseUpdatedWebhookHandler`
- `ExpenseDeletedWebhookHandler`
- `ExpenseRestoredWebhookHandler`

Each handler delegates to `MileageConversionService`.

### MileageConversionService

Core domain service.

Responsibilities:
- Load settings.
- Fetch full Clockify expense.
- Determine eligibility.
- Calculate amount.
- Update Clockify expense.
- Append note marker.
- Persist audit state.
- Avoid loops.

### ClockifyExpenseGateway

Thin abstraction over generated OpenAPI expense API client.

Methods:
- `getExpense(workspaceId, expenseId)`
- `createExpense(workspaceId, request)`
- `createExpenseWithReceipt(workspaceId, request, file)`
- `updateExpense(workspaceId, expenseId, request)`
- `updateExpenseWithReceipt(workspaceId, expenseId, request, file)`
- `listCategories(workspaceId)`
- `getCategory(workspaceId, categoryId)`

### MileageSettingsService

Stores/retrieves workspace-level settings.

Can use:
- Clockify add-on settings API, if adequate
- local database, if custom UI and dynamic category selectors are needed
- both, with local DB as cache/effective config

### UI

Single sidebar component accessible to everyone:
- Mileage entry form
- My recent submissions
- Admin tab visible only to admins
- Diagnostics
- Conversion log

Custom settings UI is recommended because category selectors should be dynamic.

## 4. Flow: add-on-created expense

```text
User opens Mileage sidebar
User enters miles + selects project/task + optional receipt
UI POSTs to /api/mileage/expenses
Backend calculates amount
Backend creates Clockify flat expense
Backend uploads receipt with expense create/update as supported
Backend stores conversion record
UI shows success + Clockify expense ID/link
```

## 5. Flow: native/mobile conversion

```text
User creates native Clockify mileage expense in input unit category
Clockify sends EXPENSE_CREATED
Backend verifies webhook
Backend fetches full expense
Backend confirms category == inputCategoryId
Backend confirms no marker and no conversion record
Backend calculates amount
Backend updates same expense:
  categoryId = outputCategoryId
  amount = rounded amount
  notes = appended audit note + marker
Backend stores conversion record
Clockify sends EXPENSE_UPDATED
Backend fetches expense
Backend sees output category/marker/existing conversion
Backend ignores event
```

## 6. Flow: update repair path

```text
EXPENSE_UPDATED received
Fetch full expense
If expense still belongs to input category and is not converted:
  run conversion
Else:
  ignore with reason
```

## 7. Flow: delete/restore

### Delete

```text
EXPENSE_DELETED received
Mark conversion row DELETED
Do not delete audit row
Do not recreate expense
```

### Restore

```text
EXPENSE_RESTORED received
Fetch full expense
If restored as input category and not converted:
  convert
Else:
  mark RESTORED_IGNORED or no-op
```

## 8. Multi-region / environment handling

Never hardcode Clockify API URLs. Use token claims / installed payload environment URLs where provided. Store per-installation context.

## 9. Failure handling

Webhook handlers should return 2xx after safely recording failure unless Clockify retry behavior is intentionally desired. Prefer recording failures internally and offering admin retry over causing repeated webhook retries.

## 10. Sequence diagrams

### Native conversion

```text
Clockify -> Add-on: POST EXPENSE_CREATED
Add-on -> Add-on: verify signature
Add-on -> DB: load settings
Add-on -> Clockify: GET expense
Clockify -> Add-on: full expense
Add-on -> DB: insert conversion status=CONVERTING
Add-on -> Clockify: PUT expense flat amount/category/note
Clockify -> Add-on: updated expense
Add-on -> DB: update conversion status=CONVERTED
Clockify -> Add-on: POST EXPENSE_UPDATED
Add-on -> Clockify: GET expense
Add-on -> Add-on: detect marker/output category
Add-on -> DB: record ignored update
```

### Add-on form create

```text
Browser iframe -> Add-on: POST /api/mileage/expenses
Add-on -> DB: load settings
Add-on -> Add-on: calculate amount
Add-on -> Clockify: POST expense
Clockify -> Add-on: expense reference
Add-on -> DB: insert conversion source=ADDON_FORM status=CONVERTED
Add-on -> Browser iframe: success
```
