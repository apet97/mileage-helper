# Technical Specification: Mileage for Clockify

## 1. Objective

Maintain a Java-based Clockify add-on using the local platform modules, vendored official add-on SDK artifacts, and `clockify-rest-client`. The add-on creates and converts mileage reimbursements into real Clockify flat expenses.

## 2. Platform assumptions

- Manifest contract targets official Clockify manifest schema `1.5`.
- Hosted add-on exposes:
  - `/manifest`
  - lifecycle endpoints
  - webhook endpoints
  - iframe UI endpoints
  - internal authenticated API endpoints
- Add-on runs as a Spring Boot service.
- Existing OpenAPI-generated expense client can:
  - create flat expenses
  - update expenses
  - create/update categories
  - upload/download receipts
  - list/fetch expenses
- Official Java SDK is used for manifest/lifecycle/webhook/component abstractions where supported.

## 3. Manifest strategy

### Preferred

Use official SDK builder for schema 1.5 if present in the installed SDK version:

```java
ClockifyManifest.v1_5Builder()
```

### Fallback

If the installed official SDK does not expose a `v1_5Builder()`, still target schema 1.5 by serving a valid manifest JSON manually or adapting the SDK model output before serialization.

Do not downgrade the manifest contract to 1.3/1.4 if schema 1.5 is required for `EXPENSE_RESTORED`.

## 4. Add-on endpoints

### Public Clockify-facing endpoints

| Endpoint | Method | Purpose |
|---|---:|---|
| `/manifest` | GET | Returns schema 1.5 manifest |
| `/lifecycle/installed` | POST | Stores installation context/token metadata |
| `/lifecycle/deleted` | POST | Cleans workspace data |
| `/lifecycle/settings-updated` | POST | Handles settings changes |
| `/lifecycle/status-changed` | POST | Enables/disables behavior |
| `/webhook/expense-created` | POST | Primary native conversion trigger |
| `/webhook/expense-updated` | POST | Repair path / loop-safe eligibility recheck |
| `/webhook/expense-deleted` | POST | Mark conversion deleted |
| `/webhook/expense-restored` | POST | Recheck restored expense eligibility |
| `/iframe/mileage` | GET | User/admin UI |

### Internal iframe API endpoints

| Endpoint | Method | Access | Purpose |
|---|---:|---|---|
| `/api/mileage/settings` | GET | Admin | Read effective workspace settings |
| `/api/mileage/settings` | PUT | Admin | Save workspace settings |
| `/api/mileage/options/categories` | GET | Admin | List expense categories for mapping |
| `/api/mileage/expenses` | POST | User | Create flat Clockify expense from mileage entry |
| `/api/mileage/conversions` | GET | Admin | List conversion audit records |
| `/api/mileage/conversions/{id}` | GET | Admin | Read conversion detail |
| `/api/mileage/conversions/{id}/retry` | POST | Admin | Retry failed/skipped conversion where safe |
| `/api/mileage/diagnostics` | GET | Admin | Show install/API/category/config health |
| `/api/mileage/preview` | POST | User | Preview calculation without creating expense |

## 5. Settings

### Required settings

| Setting | Type | Required | Notes |
|---|---|---:|---|
| `mileage.enabled` | boolean | yes | Master toggle |
| `mileage.rate` | decimal string | yes | Use BigDecimal; e.g. `0.655` |
| `mileage.unit` | string | yes | `mi`, `mile`, `km`, etc. |
| `mileage.inputCategoryId` | string | yes for native conversion | Unit-based category users select in native Clockify |
| `mileage.outputCategoryId` | string | yes | Flat category used for real expense |
| `mileage.roundingMode` | enum | yes | `HALF_UP` default |
| `mileage.convertOnCreate` | boolean | yes | Convert native created expenses |
| `mileage.convertOnUpdate` | boolean | yes | Repair path, default true but guarded |
| `mileage.preserveOriginalNotes` | boolean | yes | Append instead of replace |
| `mileage.auditMarkerEnabled` | boolean | yes | Must remain true for loop prevention |

### Optional settings

| Setting | Type | Notes |
|---|---|---|
| `mileage.currencyLabel` | string | Display-only; do not force if Clockify derives currency |
| `mileage.defaultBillable` | boolean/null | Optional default for add-on-created expenses |
| `mileage.noteTemplate` | string | Template for conversion note |
| `mileage.skipFinalizedRecords` | boolean | Default true |
| `mileage.dryRunMode` | boolean | Log but do not update |
| `mileage.allowUserRateOverride` | boolean | If false, use admin rate only |

## 6. Conversion marker

Append a machine-readable marker to converted expenses:

```text
[MileageAddon:converted:v1 id={conversionId}]
```

Human-readable note format:

```text
Mileage reimbursement: {miles} {unit} × {rate} = {amount}. Converted by Mileage for Clockify. [MileageAddon:converted:v1 id={conversionId}]
```

## 7. Calculation

Use `BigDecimal`.

```java
BigDecimal calculated = miles.multiply(rate);
BigDecimal rounded = calculated.setScale(2, roundingMode);
```

Default rounding mode:

```java
RoundingMode.HALF_UP
```

Do not use `double`, `Double`, `float`, or `Float` for money/rate/miles.

## 8. Conversion eligibility

A webhook expense is eligible only if all conditions are true:

1. Add-on is enabled for workspace.
2. Expense exists and belongs to the webhook workspace.
3. Expense category equals configured `inputCategoryId`.
4. Expense category does not equal configured `outputCategoryId`.
5. Expense note does not already contain `[MileageAddon:converted:v1`.
6. No successful conversion row exists for `(workspaceId, expenseId)`.
7. Expense has valid positive quantity/miles.
8. Expense is not locked/finalized/approved/invoiced when those states are detectable.
9. Dry-run mode is false.

If dry-run mode is true, create an audit record with status `DRY_RUN` and do not update Clockify.

## 9. Webhook event behavior

### `EXPENSE_CREATED`

Primary native conversion trigger.

Algorithm:
1. Verify signature.
2. Parse payload.
3. Extract expense ID or full fields.
4. Fetch full expense from Clockify.
5. Run `convertIfEligible`.

### `EXPENSE_UPDATED`

Repair path only.

Algorithm:
1. Verify signature.
2. Fetch full expense.
3. Run eligibility checks.
4. Convert only if still in input category and not already converted.
5. If output category or marker exists, record/return ignored.

### `EXPENSE_DELETED`

1. Verify signature.
2. Mark existing conversion row as `DELETED`.
3. Do not recreate expense.
4. Keep audit row.

### `EXPENSE_RESTORED`

1. Verify signature.
2. Fetch full expense if payload is reference-only.
3. Recheck eligibility.
4. Convert only if restored as input category and not already converted.
5. Otherwise mark `RESTORED_IGNORED`.

## 10. Add-on-created mileage expense flow

Endpoint: `POST /api/mileage/expenses`

Content types:
- `application/json`
- `multipart/form-data` when receipt upload is included

Request fields:
- `date`
- `projectId`
- `taskId` optional
- `userId`
- `miles`
- `rate` optional depending on settings
- `billable`
- `notes`
- `file` optional

Flow:
1. Authenticate iframe user token.
2. Load settings.
3. Validate user/project/category.
4. Calculate rounded amount.
5. Create flat Clockify expense using `outputCategoryId`.
6. Attach receipt if provided.
7. Store conversion row with source `ADDON_FORM`.
8. Return created expense reference and conversion metadata.

## 11. Error handling

All errors returned to iframe must be client-safe.

Examples:
- `configuration_missing`
- `invalid_mileage_quantity`
- `clockify_api_error`
- `expense_not_eligible`
- `expense_finalized`
- `conversion_already_exists`
- `receipt_upload_failed`
- `webhook_verification_failed`

## 12. Idempotency

Use all of the following:

1. Unique constraint on `(workspace_id, expense_id)`.
2. Conversion marker in note.
3. Category guard.
4. Optional raw event hash table.
5. Transaction around conversion row creation/update.

Recommended status transition:

```text
RECEIVED -> FETCHED -> ELIGIBLE -> CONVERTING -> CONVERTED
RECEIVED -> SKIPPED
RECEIVED -> FAILED
CONVERTED -> DELETED
CONVERTED -> RESTORED_IGNORED
```

## 13. Logging

Do not log tokens, receipt bytes, or full personal payloads.

Log:
- workspace ID
- expense ID
- event type
- conversion ID
- status
- sanitized error code
- API status code
- duration

## 14. Validation

Validate:
- `miles > 0`
- `rate > 0`
- `amount <= configured max`, if added
- category IDs are set
- output category is flat/non-unit, if detectable
- input category is unit-based, if detectable
- file size/type follows Clockify constraints
