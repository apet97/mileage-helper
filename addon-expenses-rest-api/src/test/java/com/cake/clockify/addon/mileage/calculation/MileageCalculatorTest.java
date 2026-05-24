package com.cake.clockify.addon.mileage.calculation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MileageCalculatorTest {
    private final MileageCalculator calculator = new MileageCalculator();

    @Test
    void calculates374MilesAt0655HalfUp() {
        MileageCalculation result = calculator.calculate("37.4", "0.655", RoundingMode.HALF_UP);
        assertThat(result.calculatedAmount()).isEqualByComparingTo(new BigDecimal("24.4970"));
        assertThat(result.roundedAmount()).isEqualByComparingTo(new BigDecimal("24.50"));
        assertThat(result.calculatedAmountText()).isEqualTo("24.4970");
        assertThat(result.roundedAmountText()).isEqualTo("24.50");
    }

    @Test
    void roundsOneMileAt0655HalfUpTo066() {
        assertThat(calculator.calculate("1", "0.655", RoundingMode.HALF_UP).roundedAmountText()).isEqualTo("0.66");
    }

    @Test
    void roundsTenMilesAt0655HalfUpTo655() {
        assertThat(calculator.calculate("10", "0.655", RoundingMode.HALF_UP).roundedAmountText()).isEqualTo("6.55");
    }

    @Test
    void rejectsZeroMiles() {
        assertThatThrownBy(() -> calculator.calculate("0", "0.655", RoundingMode.HALF_UP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("miles must be greater than zero");
    }

    @Test
    void rejectsNegativeMiles() {
        assertThatThrownBy(() -> calculator.calculate("-1", "0.655", RoundingMode.HALF_UP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("miles must be greater than zero");
    }

    @Test
    void rejectsZeroRate() {
        assertThatThrownBy(() -> calculator.calculate("1", "0", RoundingMode.HALF_UP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rate must be greater than zero");
    }

    @Test
    void supportsHalfEvenAndDown() {
        assertThat(calculator.calculate("1", "2.345", RoundingMode.HALF_EVEN).roundedAmountText()).isEqualTo("2.34");
        assertThat(calculator.calculate("1", "2.349", RoundingMode.DOWN).roundedAmountText()).isEqualTo("2.34");
    }

    @Test
    void outputUsesPlainDecimalStrings() {
        MileageCalculation result = calculator.calculate("1000000.000", "0.000001", RoundingMode.HALF_UP);
        assertThat(result.milesText()).isEqualTo("1000000");
        assertThat(result.rateText()).isEqualTo("0.000001");
        assertThat(result.calculatedAmountText()).isEqualTo("1.000000000");
        assertThat(result.roundedAmountText()).isEqualTo("1.00");
    }
}
