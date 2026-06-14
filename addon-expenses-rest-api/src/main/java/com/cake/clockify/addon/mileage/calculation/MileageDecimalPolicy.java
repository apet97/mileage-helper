package com.cake.clockify.addon.mileage.calculation;

import java.math.BigDecimal;

public final class MileageDecimalPolicy {
    public static final int MAX_DECIMAL_TEXT_LENGTH = 32;
    public static final BigDecimal MAX_MILES = new BigDecimal("1000000");
    public static final BigDecimal MAX_RATE = new BigDecimal("10000");
    public static final int MAX_MILES_SCALE = 3;
    public static final int MAX_RATE_SCALE = 6;

    private MileageDecimalPolicy() {
    }

    public static BigDecimal parseMiles(String raw) {
        return parsePositive("miles", raw, true, MAX_MILES, MAX_MILES_SCALE);
    }

    public static BigDecimal parseRate(String raw) {
        return parsePositive("rate", raw, true, MAX_RATE, MAX_RATE_SCALE);
    }

    public static BigDecimal parseOptionalRate(String raw) {
        return parsePositive("rate", raw, false, MAX_RATE, MAX_RATE_SCALE);
    }

    private static BigDecimal parsePositive(String field, String raw, boolean required, BigDecimal max, int maxScale) {
        if (raw == null || raw.isBlank()) {
            if (required) {
                throw new IllegalArgumentException(field + " is required");
            }
            return null;
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
            validateDomain(field, value, max, maxScale);
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a decimal number", e);
        }
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
