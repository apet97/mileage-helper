# Mileage for Clockify Endpoints

All API routes are scoped by the verified Clockify add-on JWT claims. There are no workspace IDs in request paths.

## User Routes

- `POST /api/mileage/preview`
- `POST /api/mileage/expenses`

## Admin Routes

- `GET /api/mileage/settings`
- `PUT /api/mileage/settings`
- `GET /api/mileage/options/categories`
- `GET /api/mileage/diagnostics`
- `GET /api/mileage/conversions`
- `GET /api/mileage/conversions/{id}`
- `POST /api/mileage/conversions/{id}/retry`

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
