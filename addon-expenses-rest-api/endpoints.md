# Mileage for Clockify Endpoints

All API routes are scoped by the verified Clockify add-on JWT claims. There are no workspace IDs in request paths.

## User Routes

- `GET /api/mileage/create-context` - safe create-page settings for verified users: configured rate, fixed `mile` unit, fixed `HALF_UP` rounding, completeness, diagnostics, and whether rate override is allowed.
- `GET /api/mileage/mine?page=0&pageSize=50&from=YYYY-MM-DD&to=YYYY-MM-DD` - current user's visible mileage rows for the verified workspace, excluding `DELETED` rows, filtered by inclusive expense date and sorted by `expenseDate DESC, updatedAt DESC`. Missing `from` and `to` defaults to the current US week, Sunday through Saturday.
- `GET /api/mileage/mine.csv?from=YYYY-MM-DD&to=YYYY-MM-DD` - CSV export for the current user's visible mileage rows with the same expense-date filtering and `DELETED` exclusion.
- `POST /api/mileage/preview` - preview miles x active rate using workspace settings unless rate override is allowed and supplied.
- `POST /api/mileage/expenses` - create a Clockify expense for the verified claims user; defaults billable to true when omitted and never accepts `userId` or `taskId`.

## Admin Routes

- `GET /api/mileage/settings`
- `PUT /api/mileage/settings` - body includes the admin-editable `noteTemplate` (converted-note template, capped at 500 chars; over-length returns 400). Rate validation uses the same decimal bounds as create/preview. A fresh workspace with no saved row reports the default rate `0.725`. Response may include `warnings` when settings saved but best-effort Clockify category price sync failed.
- `POST /api/mileage/settings/mileage-category`
- `GET /api/mileage/options/categories`
- `GET /api/mileage/options/projects`
- `GET /api/mileage/options/users` - admin user directory for the Team/Conversions user filter (from `gateway.listUsers`).
- `GET /api/mileage/diagnostics` - returns installation/settings/native-conversion readiness, warning messages, first-run checklist items, and webhook operational health (`pendingJobs`, `claimedJobs`, `failedJobs`, `oldestPendingAgeSeconds`, `lastCompletedJobAt`).
- `GET /api/mileage/team?page=0&pageSize=50&userId=&from=YYYY-MM-DD&to=YYYY-MM-DD` - admin/team visible mileage rows, excluding `DELETED`. Optional `userId` filters to one user.
- `GET /api/mileage/team.csv?userId=&from=YYYY-MM-DD&to=YYYY-MM-DD` - admin/team visible CSV export, excluding `DELETED`. Optional `userId` filters to one user.
- `GET /api/mileage/conversions?page=0&pageSize=50&status=CONVERTED&userId=&from=YYYY-MM-DD&to=YYYY-MM-DD` - audit view. Includes `DELETED` rows unless a different status filter is supplied. Optional `userId` filters to one user.
- `GET /api/mileage/conversions.csv?userId=&from=YYYY-MM-DD&to=YYYY-MM-DD` - audit CSV export. Includes `DELETED` rows. Optional `userId` filters to one user.
- `GET /api/mileage/conversions/{id}`
- `POST /api/mileage/conversions/{id}/retry`

CSV exports use `text/csv;charset=UTF-8` and include:

`expense_id,source,source_label,status,user_id,user_name,project_id,project_name,miles,rate,calculated_amount,expense_amount,rounding_mode,expense_date,updated_at,converted_at,note_marker`

`user_name` is resolved live via `gateway.listUsers` (admin team/conversions exports only; empty in `mine.csv`). `project_name` is resolved live via `gateway.listProjects` for all three exports. Both helpers return an empty map on network/`IOException`/`RuntimeException` so the export still ships with IDs intact and the name cells blank.

CSV exports paginate server-side up to 100000 rows. Responses include `X-Mileage-Export-Truncated` so callers can detect capped exports.

## Iframe Routes

- `GET /iframe/mileage`
- `GET /iframe/settings`
- `GET /iframe/report?scope=mine|team&userId=&from=YYYY-MM-DD&to=YYYY-MM-DD` - printable expense report (server-rendered HTML). Lists ALL Clockify expenses in the range; expenses the add-on converted (a CONVERTED `mileage_conversion` matched by `expenseId`) render the add-on's reconciled miles/rate/amount and category `Mileage`, everything else renders native Clockify values. `scope=mine` (or any non-admin) = the requester's own. An admin with `scope=team` (or no scope) and no `userId` = all users (adds a User column); admin with `userId` = that user. Single-user labels are resolved server-side from Clockify users, so clients pass only scope/user/date parameters, not display names. A non-admin always gets their own (foreign `userId` ignored). `from`/`to` are required. If the live Clockify expense list cannot be fetched it degrades to reconciled mileage rows only with a banner (never 500). Uses external `/assets/mileage/report.css` and `/assets/mileage/report.js`; capped at 1000 rows with a visible truncation notice (separate notices for the row cap vs. the expense-scan page budget).

## Platform Routes

- `GET /manifest`
- `POST /lifecycle/installed`
- `POST /lifecycle/deleted`
- `POST /lifecycle/settings-updated`
- `POST /lifecycle/status-changed`
- `POST /webhook/expense-created`
- `POST /webhook/expense-updated`
- `POST /webhook/expense-deleted`
- `POST /webhook/expense-restored`
