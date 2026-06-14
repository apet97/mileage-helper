package com.cake.clockify.addon.mileage.calculation;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class MileageCalculator {
    public MileageCalculation calculate(String milesText, String rateText, RoundingMode roundingMode) {
        BigDecimal miles = MileageDecimalPolicy.parseMiles(milesText);
        BigDecimal rate = MileageDecimalPolicy.parseRate(rateText);
        RoundingMode mode = roundingMode == null ? RoundingMode.HALF_UP : roundingMode;
        BigDecimal calculated = miles.multiply(rate);
        BigDecimal rounded = calculated.setScale(2, mode);
        return new MileageCalculation(miles, rate, calculated, rounded, mode);
    }
}
