# Mileage Models

Mileage DTOs live under `com.cake.clockify.addon.mileage.api.model`.

- `MileagePreviewRequest` / `MileagePreviewResponse`
- `MileageCreateContextResponse`
- `CreateMileageExpenseRequest` / `MileageCreateExpenseResponse`
- `MileageSettingsRequest` / `MileageSettingsResponse`
- `MileageCategoryOptionsResponse`
- `MileageProjectOptionsResponse`
- `MileageDiagnosticsResponse`
- `MileageRatePolicyRequest` / `MileageRatePolicyResponse` / `MileageRatePolicyListResponse`
- `MileageInsightsResponse`
- `MileageConversionDetailResponse`
- `MileageConversionListResponse`
- `MileageConversionRetryResponse`
- `MileageErrorResponse`

Persistent mileage state is split between `MileageWorkspaceSettings`, `MileageRatePolicy`, and `MileageConversion`.

`CreateMileageExpenseRequest` intentionally has no `userId` or `taskId`. The server derives the user from verified claims, stores null task IDs for manual add-on-created expenses, and defaults missing `billable` to true. Optional trip evidence fields (`tripOrigin`, `tripDestination`, `tripPurpose`, `odometerStart`, `odometerEnd`, `policyExceptionReason`) are stored on the audit row only and are not appended to Clockify notes.

`MileageConversion` stores `expenseDate` separately from audit timestamps. Manual add-on rows use the submitted form date, native/mobile rows use the Clockify expense date, and list/export APIs filter on this actual expense date rather than `updatedAt`. It also stores rate source/policy identity (`rateSource`, `ratePolicyId`, `ratePolicyName`) and optional manual trip evidence.

`MileageCreateContextResponse` is the non-admin create-page contract. It exposes the effective rate, rate source/policy identity, fixed `mile` unit, fixed `HALF_UP` rounding, and setup diagnostics, but never installation tokens or admin-only settings payloads.

`MileagePreviewRequest` may include an expense date so policy rates resolve historically. `MileagePreviewResponse`, `MileageCreateExpenseResponse`, and `MileageConversionDetailResponse` echo the rate source/policy identity used for auditability.

`MileageSettingsResponse` keeps legacy input/output category fields for compatibility and also exposes the single `mileageCategoryId`, `mileageCategoryName`, `fixedUnit`, and `fixedRoundingMode` fields used by the current admin UI.

`MileageRatePolicy*` DTOs describe admin-managed effective-dated policy rows. Deleting a policy deactivates it instead of removing audit history.

`MileageInsightsResponse` is admin-only aggregate data from `mileage_conversion`; it is not a metrics payload and must not introduce identifier tags.

`ExpenseRefWebhookPayload` accepts both `id` and `expenseId`; handlers call `effectiveExpenseId()` so updated/deleted webhook variants do not leave stale audit rows visible.

No mileage, rate, or money DTO/entity in the mileage package uses floating point types. Values are parsed as `BigDecimal` in the calculator, settings service, gateway command layer, and audit entity.
