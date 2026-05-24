# Mileage Models

Mileage DTOs live under `com.cake.clockify.addon.mileage.api.model`.

- `MileagePreviewRequest` / `MileagePreviewResponse`
- `CreateMileageExpenseRequest` / `MileageCreateExpenseResponse`
- `MileageSettingsRequest` / `MileageSettingsResponse`
- `MileageCategoryOptionsResponse`
- `MileageDiagnosticsResponse`
- `MileageConversionDetailResponse`
- `MileageConversionListResponse`
- `MileageConversionRetryResponse`
- `MileageErrorResponse`

Persistent mileage state is split between `MileageWorkspaceSettings` and `MileageConversion`.

No mileage, rate, or money DTO/entity in the mileage package uses floating point types. Values are parsed as `BigDecimal` in the calculator, settings service, gateway command layer, and audit entity.
