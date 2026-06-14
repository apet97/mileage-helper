package com.cake.clockify.addon.mileage.calculation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MileageDecimalPolicyTest {

    @Test
    void parsesValidRate() {
        assertThat(MileageDecimalPolicy.parseRate("0.725")).isEqualByComparingTo(new BigDecimal("0.725"));
    }

    @Test
    void optionalBlankRateReturnsNull() {
        assertThat(MileageDecimalPolicy.parseOptionalRate("")).isNull();
    }

    @Test
    void rejectsRateAboveDomainMaximum() {
        assertThatThrownBy(() -> MileageDecimalPolicy.parseRate("10000.000001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rate must be at most 10000");
    }

    @Test
    void rejectsTooManyRateFractionDigits() {
        assertThatThrownBy(() -> MileageDecimalPolicy.parseRate("0.1234567"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rate supports at most 6 decimal places");
    }

    @Test
    void rejectsExponentRateNotation() {
        assertThatThrownBy(() -> MileageDecimalPolicy.parseRate("1E+3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rate must be a plain decimal number");
    }

    @Test
    void rejectsTooLongRateInput() {
        assertThatThrownBy(() -> MileageDecimalPolicy.parseRate("123456789012345678901234567890123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rate must be 32 characters or fewer");
    }

    @Test
    void rejectsTooManyMileageFractionDigits() {
        assertThatThrownBy(() -> MileageDecimalPolicy.parseMiles("12.3456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("miles supports at most 3 decimal places");
    }
}
