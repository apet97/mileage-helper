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
        // "/mine" own rows: the user is the requester and the Mine table has no User column, so leave
        // userName null rather than echoing the raw userId.
        return new MileageConversionListResponse(
                page.getContent().stream()
                        .map(conversion -> MileageConversionDetailResponse.from(conversion, null, false))
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    public static MileageConversionListResponse from(
            Page<com.cake.clockify.addon.mileage.audit.MileageConversion> page,
            Map<String, String> userNamesById) {
        return new MileageConversionListResponse(
                page.getContent().stream()
                        .map(conversion -> MileageConversionDetailResponse.from(
                                conversion,
                                userNameFor(conversion, userNamesById)))
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    private static String userNameFor(
            com.cake.clockify.addon.mileage.audit.MileageConversion conversion,
            Map<String, String> userNamesById) {
        String userId = conversion.getUserId();
        return userId == null || userId.isBlank() ? null : userNamesById.get(userId);
    }
}
