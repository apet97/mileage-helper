package com.cake.clockify.addon.mileage.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record MileageCalculation(
        BigDecimal miles,
        BigDecimal rate,
        BigDecimal calculatedAmount,
        BigDecimal roundedAmount,
        RoundingMode roundingMode
) {
    public String milesText() {
        return miles.stripTrailingZeros().toPlainString();
    }

    public String rateText() {
        return rate.stripTrailingZeros().toPlainString();
    }

    public String calculatedAmountText() {
        return calculatedAmount.toPlainString();
    }

    public String roundedAmountText() {
        return roundedAmount.setScale(2, roundingMode).toPlainString();
    }
}
