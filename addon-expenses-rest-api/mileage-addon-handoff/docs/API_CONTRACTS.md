# API Contracts

## 1. Internal API authentication

Internal API endpoints are called from Clockify iframe UI. Authenticate using the user token supplied to the iframe or the SDK-supported request authentication mechanism.

Never send installation tokens to the frontend.

## 2. `POST /api/mileage/preview`

Preview mileage calculation.

### Request

```json
{
  "miles": "37.4",
  "rate": "0.655"
}
```

### Response

```json
{
  "miles": "37.4",
  "rate": "0.655",
  "calculatedAmount": "24.4970",
  "roundedAmount": "24.50",
  "roundingMode": "HALF_UP"
}
```

## 3. `POST /api/mileage/expenses`

Create a real Clockify flat expense from add-on form.

### JSON request

```json
{
  "date": "2026-05-24",
  "userId": "user_id",
  "projectId": "project_id",
  "taskId": "task_id",
  "miles": "37.4",
  "rate": "0.655",
  "billable": true,
  "notes": "Client site visit"
}
```

### Multipart request

Fields:
- `date`
- `userId`
- `projectId`
- `taskId`
- `miles`
- `rate`
- `billable`
- `notes`
- `file`

### Response

```json
{
  "conversionId": "3f09b8f0-7c9e-4b8f-8be7-d13ad629aa92",
  "expenseId": "clockify_expense_id",
  "status": "CONVERTED",
  "miles": "37.4",
  "rate": "0.655",
  "roundedAmount": "24.50",
  "noteMarker": "[MileageAddon:converted:v1 id=3f09b8f0-7c9e-4b8f-8be7-d13ad629aa92]"
}
```

## 4. `GET /api/mileage/settings`

Admin-only. Returns effective settings.

### Response

```json
{
  "enabled": true,
  "rate": "0.655",
  "unit": "mi",
  "inputCategoryId": "cat_input",
  "outputCategoryId": "cat_output",
  "roundingMode": "HALF_UP",
  "convertOnCreate": true,
  "convertOnUpdate": true,
  "preserveOriginalNotes": true,
  "dryRunMode": false,
  "allowUserRateOverride": false,
  "noteTemplate": "Mileage reimbursement: {{miles}} {{unit}} × {{rate}} = {{amount}}. {{marker}}"
}
```

## 5. `PUT /api/mileage/settings`

Admin-only. Saves settings.

### Request

```json
{
  "enabled": true,
  "rate": "0.655",
  "unit": "mi",
  "inputCategoryId": "cat_input",
  "outputCategoryId": "cat_output",
  "roundingMode": "HALF_UP",
  "convertOnCreate": true,
  "convertOnUpdate": true,
  "preserveOriginalNotes": true,
  "dryRunMode": false,
  "allowUserRateOverride": false
}
```

### Response

```json
{
  "status": "OK"
}
```

## 6. `GET /api/mileage/options/categories`

Admin-only. Lists categories for setup UI.

### Response

```json
{
  "categories": [
    {
      "id": "cat_input",
      "name": "Mileage Input",
      "unit": "mi",
      "unitPrice": "0.01",
      "archived": false,
      "type": "UNIT"
    },
    {
      "id": "cat_output",
      "name": "Mileage Reimbursement",
      "archived": false,
      "type": "FLAT"
    }
  ]
}
```

Exact category fields depend on Clockify API response; normalize in backend.

## 7. `GET /api/mileage/conversions`

Admin-only. Lists conversions.

Query params:
- `status`
- `expenseId`
- `page`
- `pageSize`

### Response

```json
{
  "items": [
    {
      "id": "3f09b8f0-7c9e-4b8f-8be7-d13ad629aa92",
      "expenseId": "clockify_expense_id",
      "source": "WEBHOOK_CREATED",
      "status": "CONVERTED",
      "miles": "37.4",
      "rate": "0.655",
      "roundedAmount": "24.50",
      "convertedAt": "2026-05-24T10:30:00Z"
    }
  ],
  "page": 1,
  "pageSize": 25,
  "total": 1
}
```

## 8. `POST /api/mileage/conversions/{id}/retry`

Admin-only. Retry failed/skipped conversion if safe.

### Response

```json
{
  "id": "3f09b8f0-7c9e-4b8f-8be7-d13ad629aa92",
  "status": "CONVERTED",
  "message": "Conversion completed"
}
```

## 9. Webhook payload handling

Do not rely on payload completeness.

Webhook handlers should:
1. Verify signature.
2. Extract expense ID.
3. Fetch full expense from Clockify before conversion.
4. Compare fetched workspace ID to claims workspace ID when field exists.
5. Run conversion logic.

## 10. Error response shape

```json
{
  "error": "configuration_missing",
  "message": "Mileage output category is not configured",
  "fields": {
    "outputCategoryId": "Required"
  }
}
```
