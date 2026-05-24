# Mileage for Clockify Endpoints

All API routes are scoped by the verified Clockify add-on JWT claims. There are no workspace IDs in request paths.

## User Routes

- `GET /api/mileage/create-context` - safe create-page settings for verified users: configured rate, fixed `mile` unit, fixed `HALF_UP` rounding, completeness, diagnostics, and whether rate override is allowed.
- `GET /api/mileage/mine?page=0&pageSize=50&from=YYYY-MM-DD&to=YYYY-MM-DD` - current user's mileage rows for the verified workspace, filtered by inclusive expense date and sorted by `expenseDate DESC, updatedAt DESC`. Missing `from` and `to` defaults to the current US week, Sunday through Saturday.
- `GET /api/mileage/mine.csv?from=YYYY-MM-DD&to=YYYY-MM-DD` - CSV export for the current user's mileage rows with the same expense-date filtering.
- `POST /api/mileage/preview` - preview miles x active rate using workspace settings unless rate override is allowed and supplied.
- `POST /api/mileage/expenses` - create a Clockify expense for the verified claims user; defaults billable to true when omitted and never accepts `userId` or `taskId`.

## Admin Routes

- `GET /api/mileage/settings`
- `PUT /api/mileage/settings`
- `POST /api/mileage/settings/mileage-category`
- `GET /api/mileage/options/categories`
- `GET /api/mileage/options/projects`
- `GET /api/mileage/diagnostics`
- `GET /api/mileage/team?page=0&pageSize=50&from=YYYY-MM-DD&to=YYYY-MM-DD`
- `GET /api/mileage/team.csv?from=YYYY-MM-DD&to=YYYY-MM-DD`
- `GET /api/mileage/conversions?page=0&pageSize=50&status=CONVERTED&from=YYYY-MM-DD&to=YYYY-MM-DD`
- `GET /api/mileage/conversions.csv?from=YYYY-MM-DD&to=YYYY-MM-DD`
- `GET /api/mileage/conversions/{id}`
- `POST /api/mileage/conversions/{id}/retry`

CSV exports use `text/csv;charset=UTF-8` and include:

`expense_id,source,source_label,status,user_id,user_name,project_id,miles,rate,calculated_amount,expense_amount,rounding_mode,expense_date,updated_at,converted_at,note_marker`

## Iframe Routes

- `GET /iframe/mileage`
- `GET /iframe/settings`

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
