package com.acme.salary.shared.domain;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Currency;

/**
 * An immutable snapshot of rates to USD. A request loads one snapshot and uses it throughout, so
 * every figure in a response is converted consistently. USD is always present at exactly 1.
 */
public final class ExchangeRates {

    private static final Currency USD = Currency.getInstance("USD");

    private final Map<Currency, BigDecimal> ratesToUsd;

    private ExchangeRates(Map<Currency, BigDecimal> ratesToUsd) {
        this.ratesToUsd = ratesToUsd;
    }

    public static ExchangeRates of(Map<Currency, BigDecimal> ratesToUsd) {
        Map<Currency, BigDecimal> copy = new HashMap<>();
        ratesToUsd.forEach((currency, rate) -> {
            if (rate == null || rate.signum() <= 0) {
                throw new IllegalArgumentException(
                        "Exchange rate for " + currency.getCurrencyCode() + " must be greater than 0");
            }
            if (currency.equals(USD) && rate.compareTo(BigDecimal.ONE) != 0) {
                throw new IllegalArgumentException("The USD rate is always 1, not " + rate);
            }
            copy.put(currency, rate);
        });
        copy.put(USD, BigDecimal.ONE);
        return new ExchangeRates(Map.copyOf(copy));
    }

    public Set<Currency> currencies() {
        return ratesToUsd.keySet();
    }

    public BigDecimal rateToUsd(Currency currency) {
        BigDecimal rate = ratesToUsd.get(currency);
        if (rate == null) {
            throw new MissingExchangeRateException(currency);
        }
        return rate;
    }

    public Money toUsd(Money money) {
        if (money.currency().equals(USD)) {
            return money;
        }
        return Money.of(money.amount().multiply(rateToUsd(money.currency()), MoneyMath.CONTEXT), USD);
    }
}
