package com.cake.clockify.addon.mileage.api.model;

import org.springframework.data.domain.Page;

import java.util.List;

public record MileageConversionListResponse(
        List<MileageConversionDetailResponse> conversions,
        int page,
        int pageSize,
        long totalElements,
        int totalPages
) {
    public static MileageConversionListResponse from(Page<com.cake.clockify.addon.mileage.audit.MileageConversion> page) {
        return new MileageConversionListResponse(
                page.getContent().stream().map(MileageConversionDetailResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
