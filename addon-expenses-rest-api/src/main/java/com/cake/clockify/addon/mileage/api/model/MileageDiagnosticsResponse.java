package com.cake.clockify.addon.mileage.api.model;

import java.util.List;

public record MileageDiagnosticsResponse(
        boolean installationAvailable,
        boolean settingsComplete,
        boolean nativeConversionReady,
        List<String> warnings
) {
}
