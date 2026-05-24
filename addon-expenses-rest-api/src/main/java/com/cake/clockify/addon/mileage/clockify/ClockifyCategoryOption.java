package com.cake.clockify.addon.mileage.clockify;

import java.math.BigDecimal;

public record ClockifyCategoryOption(
        String id,
        String name,
        String type,
        String unit,
        BigDecimal unitPrice
) {
}
