package com.acme.salary.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class RoundingTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void roundsHalfUpToTwoDecimalPlacesForDisplay() {
        assertThat(Rounding.display(new BigDecimal("1.005"))).isEqualByComparingTo("1.01");
        assertThat(Rounding.display(new BigDecimal("1.004"))).isEqualByComparingTo("1.00");
        assertThat(Rounding.display(new BigDecimal("2.5"))).isEqualByComparingTo("2.50");
    }

    @Test
    void roundsNegativeValuesAwayFromZeroOnTies() {
        assertThat(Rounding.display(new BigDecimal("-1.005"))).isEqualByComparingTo("-1.01");
    }

    @Test
    void alwaysReportsExactlyTwoDecimalPlaces() {
        assertThat(Rounding.display(new BigDecimal("7")).scale()).isEqualTo(2);
        assertThat(Rounding.display(new BigDecimal("7.123456")).scale()).isEqualTo(2);
    }

    @Test
    void roundingMoneyDoesNotChangeTheOriginal() {
        Money original = Money.of("1.005", USD);

        Money shown = Rounding.display(original);

        assertThat(shown.amount()).isEqualByComparingTo("1.01");
        assertThat(original.amount()).isEqualByComparingTo("1.005");
        assertThat(shown.currency()).isEqualTo(USD);
    }
}
