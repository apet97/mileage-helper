# Mileage Models

Mileage DTOs live under `com.cake.clockify.addon.mileage.api.model`.

- `MileagePreviewRequest` / `MileagePreviewResponse`
- `MileageCreateContextResponse`
- `CreateMileageExpenseRequest` / `MileageCreateExpenseResponse`
- `MileageSettingsRequest` / `MileageSettingsResponse`
- `MileageCategoryOptionsResponse`
- `MileageProjectOptionsResponse`
- `MileageDiagnosticsResponse`
- `MileageConversionDetailResponse`
- `MileageConversionListResponse`
- `MileageConversionRetryResponse`
- `MileageErrorResponse`

Persistent mileage state is split between `MileageWorkspaceSettings` and `MileageConversion`.

`CreateMileageExpenseRequest` intentionally has no `userId` or `taskId`. The server derives the user from verified claims, stores null task IDs for manual add-on-created expenses, and defaults missing `billable` to true.

`MileageCreateContextResponse` is the non-admin create-page contract. It can expose the configured rate/unit/rounding and setup diagnostics, but never installation tokens or admin-only settings payloads.

No mileage, rate, or money DTO/entity in the mileage package uses floating point types. Values are parsed as `BigDecimal` in the calculator, settings service, gateway command layer, and audit entity.
