package com.acme.salary.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExchangeRatesTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency INR = Currency.getInstance("INR");
    private static final Currency EUR = Currency.getInstance("EUR");

    private final ExchangeRates rates = ExchangeRates.of(Map.of(INR, new BigDecimal("0.012")));

    @Test
    void convertsToUsdWithoutRounding() {
        Money usd = rates.toUsd(Money.of("100", INR));

        assertThat(usd.currency()).isEqualTo(USD);
        assertThat(usd.amount()).isEqualByComparingTo("1.2");
    }

    @Test
    void keepsPrecisionBeyondTwoDecimalPlaces() {
        Money usd = rates.toUsd(Money.of("1234.56", INR));

        assertThat(usd.amount()).isEqualByComparingTo("14.81472");
    }

    @Test
    void usdIsAlwaysAvailableAtRateOne() {
        Money usd = rates.toUsd(Money.of("250", USD));

        assertThat(usd).isEqualTo(Money.of("250", USD));
        assertThat(rates.rateToUsd(USD)).isEqualByComparingTo("1");
    }

    @Test
    void failsLoudlyWhenARateIsMissing() {
        assertThatThrownBy(() -> rates.toUsd(Money.of("10", EUR)))
                .isInstanceOf(MissingExchangeRateException.class)
                .hasMessageContaining("EUR");
        assertThatThrownBy(() -> rates.rateToUsd(EUR)).isInstanceOf(MissingExchangeRateException.class);
    }

    @Test
    void rejectsZeroAndNegativeRates() {
        assertThatThrownBy(() -> ExchangeRates.of(Map.of(INR, BigDecimal.ZERO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INR");
        assertThatThrownBy(() -> ExchangeRates.of(Map.of(INR, new BigDecimal("-0.01"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAUsdRateThatIsNotOne() {
        assertThatThrownBy(() -> ExchangeRates.of(Map.of(USD, new BigDecimal("1.1"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USD");
    }

    @Test
    void acceptsAnExplicitUsdRateOfOne() {
        ExchangeRates withUsd = ExchangeRates.of(Map.of(USD, new BigDecimal("1.000"), INR, new BigDecimal("0.012")));

        assertThat(withUsd.currencies()).containsExactlyInAnyOrder(USD, INR);
    }

    @Test
    void listsTheCurrenciesItCanConvert() {
        assertThat(rates.currencies()).containsExactlyInAnyOrder(USD, INR);
    }

    @Test
    void rejectsRatesWithMoreDecimalPlacesThanTheDatabaseCanStore() {
        assertThatThrownBy(() -> ExchangeRates.of(Map.of(INR, new BigDecimal("0.123456789"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INR")
                .hasMessageContaining("8 decimal places");
        // trailing zeros are not real precision
        assertThat(ExchangeRates.of(Map.of(INR, new BigDecimal("0.0120000000"))).rateToUsd(INR))
                .isEqualByComparingTo("0.012");
    }

    @Test
    void reportsEverySupportedCurrencyThatHasNoRate() {
        Set<Currency> required = Set.of(INR, EUR, Currency.getInstance("GBP"));

        assertThatThrownBy(() -> rates.requireRatesFor(required))
                .isInstanceOf(MissingExchangeRateException.class)
                .hasMessageContaining("EUR")
                .hasMessageContaining("GBP");
        rates.requireRatesFor(Set.of(INR, USD));
    }

    @Test
    void theMissingRateExceptionNamesTheCurrencies() {
        assertThatThrownBy(() -> rates.rateToUsd(EUR))
                .isInstanceOfSatisfying(MissingExchangeRateException.class,
                        e -> assertThat(e.currencies()).containsExactly(EUR));
    }

    @Test
    void theSnapshotIsImmutable() {
        Map<Currency, BigDecimal> source = new HashMap<>();
        source.put(INR, new BigDecimal("0.012"));
        ExchangeRates snapshot = ExchangeRates.of(source);

        source.put(INR, new BigDecimal("99"));

        assertThat(snapshot.rateToUsd(INR)).isEqualByComparingTo("0.012");
    }
}
