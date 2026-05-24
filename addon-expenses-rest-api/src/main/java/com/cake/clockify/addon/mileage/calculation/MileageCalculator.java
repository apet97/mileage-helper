package com.cake.clockify.addon.mileage.calculation;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class MileageCalculator {
    public MileageCalculation calculate(String milesText, String rateText, RoundingMode roundingMode) {
        BigDecimal miles = parsePositive("miles", milesText);
        BigDecimal rate = parsePositive("rate", rateText);
        RoundingMode mode = roundingMode == null ? RoundingMode.HALF_UP : roundingMode;
        BigDecimal calculated = miles.multiply(rate);
        BigDecimal rounded = calculated.setScale(2, mode);
        return new MileageCalculation(miles, rate, calculated, rounded, mode);
    }

    private static BigDecimal parsePositive(String field, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(field + " must be greater than zero");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a decimal number", e);
        }
    }
}
