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

`MileageCreateContextResponse` is the non-admin create-page contract. It exposes the configured rate, fixed `mile` unit, fixed `HALF_UP` rounding, and setup diagnostics, but never installation tokens or admin-only settings payloads.

`MileageSettingsResponse` keeps legacy input/output category fields for compatibility and also exposes the single `mileageCategoryId`, `mileageCategoryName`, `fixedUnit`, and `fixedRoundingMode` fields used by the current admin UI.

No mileage, rate, or money DTO/entity in the mileage package uses floating point types. Values are parsed as `BigDecimal` in the calculator, settings service, gateway command layer, and audit entity.
