package com.acme.salary.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.Test;

class MoneyTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");

    @Test
    void keepsTheAmountExactlyAsGivenWithoutRounding() {
        Money money = Money.of(new BigDecimal("1.005"), USD);

        assertThat(money.amount()).isEqualByComparingTo("1.005");
        assertThat(money.currency()).isEqualTo(USD);
    }

    @Test
    void addsAndSubtractsMoneyOfTheSameCurrency() {
        Money a = Money.of("10.10", USD);
        Money b = Money.of("0.25", USD);

        assertThat(a.plus(b)).isEqualTo(Money.of("10.35", USD));
        assertThat(a.minus(b)).isEqualTo(Money.of("9.85", USD));
    }

    @Test
    void refusesToMixCurrencies() {
        Money dollars = Money.of("5", USD);
        Money euros = Money.of("5", EUR);

        assertThatThrownBy(() -> dollars.plus(euros))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USD")
                .hasMessageContaining("EUR");
        assertThatThrownBy(() -> dollars.minus(euros)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void multipliesByAFactorWithoutLosingPrecision() {
        Money money = Money.of("100", Currency.getInstance("INR"));

        Money converted = money.times(new BigDecimal("0.012"));

        assertThat(converted.amount()).isEqualByComparingTo("1.2");
    }

    @Test
    void usesDecimal128PrecisionForDivisionLikeResults() {
        Money third = Money.of("1", USD).times(new BigDecimal("1").divide(new BigDecimal("3"), MoneyMath.CONTEXT));

        assertThat(third.amount().precision()).isLessThanOrEqualTo(34);
        assertThat(third.amount()).isGreaterThan(new BigDecimal("0.3333333333333333333333333333333"));
    }

    @Test
    void equalityIgnoresTrailingZeros() {
        assertThat(Money.of("10.0", USD)).isEqualTo(Money.of("10.00", USD));
        assertThat(Money.of("10.0", USD)).hasSameHashCodeAs(Money.of("10.00", USD));
        assertThat(Money.of("10", USD)).isNotEqualTo(Money.of("10", EUR));
    }

    @Test
    void ordersByAmountWithinOneCurrency() {
        assertThat(Money.of("2", USD)).isGreaterThan(Money.of("1.99", USD));
        assertThatThrownBy(() -> Money.of("2", USD).compareTo(Money.of("1", EUR)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingParts() {
        assertThatThrownBy(() -> Money.of((BigDecimal) null, USD)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Money.of(BigDecimal.ONE, null)).isInstanceOf(NullPointerException.class);
    }
}
