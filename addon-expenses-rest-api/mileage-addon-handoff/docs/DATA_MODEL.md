# Data Model

## 1. Principles

- Store only what is necessary for configuration, idempotency, diagnostics, and audit.
- Do not duplicate Clockify's expense reporting data.
- Do not store receipt bytes permanently.
- Use `BigDecimal`/SQL `numeric`, never floating point, for mileage/rate/amount.

## 2. Tables

### `mileage_workspace_settings`

Workspace-level effective settings.

| Column | Type | Required | Notes |
|---|---|---:|---|
| `workspace_id` | varchar(64) PK | yes | Clockify workspace ID |
| `enabled` | boolean | yes | Master toggle |
| `rate` | numeric(18,6) | yes | Configured precise mileage rate |
| `unit` | varchar(16) | yes | `mi`, `mile`, `km` |
| `input_category_id` | varchar(64) | no | Native unit mileage category |
| `output_category_id` | varchar(64) | yes | Flat mileage category |
| `rounding_mode` | varchar(32) | yes | Java `RoundingMode` name |
| `convert_on_create` | boolean | yes | Default true |
| `convert_on_update` | boolean | yes | Default true for repair path |
| `preserve_original_notes` | boolean | yes | Default true |
| `dry_run_mode` | boolean | yes | Default false |
| `allow_user_rate_override` | boolean | yes | Default false |
| `note_template` | text | no | Custom note template |
| `created_at` | timestamp | yes | |
| `updated_at` | timestamp | yes | |
| `updated_by_user_id` | varchar(64) | no | |

### `mileage_conversion`

Audit/idempotency table.

| Column | Type | Required | Notes |
|---|---|---:|---|
| `id` | uuid PK | yes | Conversion ID used in note marker |
| `workspace_id` | varchar(64) | yes | |
| `expense_id` | varchar(64) | yes | Clockify expense ID |
| `source` | varchar(32) | yes | `ADDON_FORM`, `WEBHOOK_CREATED`, `WEBHOOK_UPDATED`, `WEBHOOK_RESTORED` |
| `source_event_type` | varchar(64) | no | Clockify event |
| `source_category_id` | varchar(64) | no | Original/input category |
| `target_category_id` | varchar(64) | no | Output flat category |
| `user_id` | varchar(64) | no | Expense user |
| `project_id` | varchar(64) | no | |
| `task_id` | varchar(64) | no | |
| `miles` | numeric(18,6) | no | Quantity extracted/entered |
| `rate` | numeric(18,6) | no | Rate applied |
| `calculated_amount` | numeric(18,6) | no | Before cent rounding |
| `rounded_amount` | numeric(18,2) | no | Amount written to Clockify |
| `currency` | varchar(16) | no | Display/audit only if known |
| `rounding_mode` | varchar(32) | no | |
| `status` | varchar(32) | yes | See statuses below |
| `skip_reason` | varchar(128) | no | For `SKIPPED` |
| `error_code` | varchar(128) | no | For `FAILED` |
| `error_message` | text | no | Sanitized; no tokens |
| `note_marker` | varchar(128) | no | `[MileageAddon:converted:v1 id=...]` |
| `raw_event_hash` | varchar(128) | no | Optional idempotency aid |
| `clockify_request_id` | varchar(128) | no | If API returns request ID |
| `created_at` | timestamp | yes | |
| `updated_at` | timestamp | yes | |
| `converted_at` | timestamp | no | |
| `deleted_at` | timestamp | no | |

Unique indexes:
- `unique(workspace_id, expense_id)` for successful/active conversion idempotency.
- optional `unique(workspace_id, raw_event_hash)` if event hash is stable.

### `mileage_webhook_event_log` optional

Use this if webhook retries/out-of-order behavior needs more detail.

| Column | Type | Notes |
|---|---|---|
| `id` | uuid PK | |
| `workspace_id` | varchar(64) | |
| `event_type` | varchar(64) | |
| `expense_id` | varchar(64) | |
| `event_hash` | varchar(128) | |
| `status` | varchar(32) | `RECEIVED`, `IGNORED`, `PROCESSED`, `FAILED` |
| `received_at` | timestamp | |
| `processed_at` | timestamp | |

## 3. Status values

### `mileage_conversion.status`

```text
RECEIVED
FETCHED
DRY_RUN
SKIPPED
CONVERTING
CONVERTED
FAILED
DELETED
RESTORED_IGNORED
```

## 4. Skip reasons

```text
ADDON_DISABLED
SETTINGS_INCOMPLETE
NOT_INPUT_CATEGORY
ALREADY_OUTPUT_CATEGORY
ALREADY_MARKED
ALREADY_CONVERTED
MISSING_QUANTITY
INVALID_QUANTITY
FINALIZED_OR_LOCKED
DRY_RUN
WORKSPACE_MISMATCH
API_RESOURCE_NOT_FOUND
```

## 5. JPA entity notes

Use:
- `UUID` for conversion ID.
- `BigDecimal` for numeric fields.
- `Instant` for timestamps.
- `@Version` optional optimistic locking.
- `@Column(precision = 18, scale = 6)` for miles/rate/calculated.
- `@Column(precision = 18, scale = 2)` for rounded amount.

Do not reuse a temporary log table as the conversion table unless renamed and migrated; the conversion table is core product state.
