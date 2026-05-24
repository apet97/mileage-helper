# Security and Privacy Requirements

## 1. Token handling

- Installation tokens must be stored encrypted or in a secure credential store.
- Installation tokens must never be sent to iframe/frontend.
- User tokens must be short-lived and treated as credentials.
- Tokens must be redacted in all logs.
- Do not include tokens in exception messages.

## 2. Webhook verification

Every webhook must be verified before processing:
- signature valid
- issuer is Clockify
- subject matches add-on key
- workspace ID claim exists
- event type header matches endpoint expectation
- payload workspace ID matches claims when payload includes workspace

## 3. Lifecycle verification

Lifecycle hooks must be verified before accepting:
- installed
- deleted
- settings updated
- status changed

## 4. Workspace isolation

Every query must filter by `workspace_id`.

Never use only `expense_id` for DB updates/deletes if `expense_id` uniqueness is not guaranteed across workspaces.

Use:
```sql
where workspace_id = ? and expense_id = ?
```

## 5. Data minimization

Store:
- IDs
- numeric conversion values
- sanitized status/error
- conversion marker
- timestamps

Do not store:
- receipt bytes
- raw tokens
- unnecessary user PII
- full webhook payloads unless sanitized and necessary for debugging

## 6. Receipt/file handling

- Validate file size before forwarding.
- Validate content type.
- Do not render arbitrary files inline.
- Use `X-Content-Type-Options: nosniff` for downloads/proxy responses.
- Do not permanently persist file bytes unless explicitly required.
- If temporary files are used, delete after forwarding.

## 7. Error responses

Client-facing errors must be sanitized.

Bad:
```text
Full upstream payload, stack trace, token
```

Good:
```json
{
  "error": "clockify_api_error",
  "message": "The Clockify API rejected the request",
  "status": 400
}
```

## 8. Logging

Log:
- workspace ID
- expense ID
- event type
- conversion ID
- status
- duration
- sanitized error code

Do not log:
- tokens
- request headers containing credentials
- receipt bytes
- full file names if sensitive
- full notes if they may contain private data

## 9. Permissions

- User mileage form can be available to everyone.
- Admin settings must require admin/owner.
- Conversion log should be admin-only unless a personal "my submissions" view is implemented.
- Use least-privilege user token when possible.
- Use installation token only for server-side webhook/backend operations that require workspace-level access.

## 10. Finalized records

Do not mutate records that are:
- approved
- locked
- invoiced
- otherwise finalized

If finalization state is not visible in fetched payload but update fails with conflict/permission/locked response, record status `FAILED` or `SKIPPED` with sanitized reason.

## 11. Rate limiting and retries

- Respect Clockify rate limits.
- On 429, store retryAfter if available.
- Avoid automatic retry storms from webhook deliveries.
- Prefer admin-visible retry for failed conversions.

## 12. Manifest and environment safety

- Do not hardcode production URLs.
- Use `ADDON_BASE_URL` to generate manifest.
- Use environment-specific API URLs from token claims/installation context.
- Validate manifest before publishing.
