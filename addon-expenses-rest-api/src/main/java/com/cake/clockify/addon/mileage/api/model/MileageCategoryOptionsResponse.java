package com.cake.clockify.addon.mileage.api.model;

import com.cake.clockify.addon.mileage.clockify.ClockifyCategoryOption;

import java.util.List;

public record MileageCategoryOptionsResponse(
        List<CategoryOption> categories
) {
    public static MileageCategoryOptionsResponse from(List<ClockifyCategoryOption> categories) {
        return new MileageCategoryOptionsResponse(categories.stream()
                .map(category -> new CategoryOption(
                        category.id(),
                        category.name(),
                        category.type(),
                        category.unit(),
                        category.unitPrice() == null ? null : category.unitPrice().toPlainString()))
                .toList());
    }

    public record CategoryOption(
            String id,
            String name,
            String type,
            String unit,
            String unitPrice
    ) {
    }
}
