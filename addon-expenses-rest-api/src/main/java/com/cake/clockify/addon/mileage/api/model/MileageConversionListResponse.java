package com.cake.clockify.addon.mileage.api.model;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

public record MileageConversionListResponse(
        List<MileageConversionDetailResponse> conversions,
        int page,
        int pageSize,
        long totalElements,
        int totalPages
) {
    public static MileageConversionListResponse from(Page<com.cake.clockify.addon.mileage.audit.MileageConversion> page) {
        return from(page, Map.of());
    }

    public static MileageConversionListResponse from(
            Page<com.cake.clockify.addon.mileage.audit.MileageConversion> page,
            Map<String, String> userNamesById) {
        return new MileageConversionListResponse(
                page.getContent().stream()
                        .map(conversion -> MileageConversionDetailResponse.from(conversion, userNamesById.get(conversion.getUserId())))
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
