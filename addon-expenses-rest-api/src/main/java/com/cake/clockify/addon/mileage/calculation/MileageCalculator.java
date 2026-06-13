package com.cake.clockify.addon.mileage.calculation;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class MileageCalculator {
    private static final int MAX_DECIMAL_TEXT_LENGTH = 32;
    private static final BigDecimal MAX_MILES = new BigDecimal("1000000");
    private static final BigDecimal MAX_RATE = new BigDecimal("10000");
    private static final int MAX_MILES_SCALE = 3;
    private static final int MAX_RATE_SCALE = 6;

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
        String text = raw.trim();
        if (text.length() > MAX_DECIMAL_TEXT_LENGTH) {
            throw new IllegalArgumentException(field + " must be 32 characters or fewer");
        }
        if (text.contains("e") || text.contains("E")) {
            throw new IllegalArgumentException(field + " must be a plain decimal number");
        }
        try {
            BigDecimal value = new BigDecimal(text);
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(field + " must be greater than zero");
            }
            validateDomain(field, value);
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a decimal number", e);
        }
    }

    private static void validateDomain(String field, BigDecimal value) {
        if ("miles".equals(field)) {
            validateDomain(field, value, MAX_MILES, MAX_MILES_SCALE);
            return;
        }
        validateDomain(field, value, MAX_RATE, MAX_RATE_SCALE);
    }

    private static void validateDomain(String field, BigDecimal value, BigDecimal max, int maxScale) {
        if (value.compareTo(max) > 0) {
            throw new IllegalArgumentException(field + " must be at most " + max.toPlainString());
        }
        int scale = Math.max(value.stripTrailingZeros().scale(), 0);
        if (scale > maxScale) {
            throw new IllegalArgumentException(field + " supports at most " + maxScale + " decimal places");
        }
    }
}
