# Mileage for Clockify Endpoints

All API routes are scoped by the verified Clockify add-on JWT claims. There are no workspace IDs in request paths.

## User Routes

- `GET /api/mileage/create-context` - safe create-page settings for verified users: configured rate/unit, rounding, completeness, diagnostics, and whether rate override is allowed.
- `GET /api/mileage/mine?page=0&pageSize=50` - current user's mileage rows for the verified workspace, sorted by `updatedAt DESC`.
- `GET /api/mileage/mine.csv` - CSV export for the current user's mileage rows.
- `POST /api/mileage/preview` - preview miles x active rate using workspace settings unless rate override is allowed and supplied.
- `POST /api/mileage/expenses` - create a flat Clockify expense for the verified claims user; defaults billable to true when omitted and never accepts `userId` or `taskId`.

## Admin Routes

- `GET /api/mileage/settings`
- `PUT /api/mileage/settings`
- `GET /api/mileage/options/categories`
- `GET /api/mileage/options/projects`
- `GET /api/mileage/diagnostics`
- `GET /api/mileage/team?page=0&pageSize=50`
- `GET /api/mileage/team.csv`
- `GET /api/mileage/conversions`
- `GET /api/mileage/conversions.csv`
- `GET /api/mileage/conversions/{id}`
- `POST /api/mileage/conversions/{id}/retry`

CSV exports use `text/csv;charset=UTF-8` and include:

`expense_id,source,status,user_id,project_id,miles,rate,calculated_amount,expense_amount,rounding_mode,updated_at,converted_at,note_marker`

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
