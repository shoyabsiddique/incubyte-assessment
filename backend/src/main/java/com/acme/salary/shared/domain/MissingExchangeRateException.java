package com.acme.salary.shared.domain;

import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

/** Thrown when a conversion needs a rate that is not in the snapshot. Never skipped silently. */
public class MissingExchangeRateException extends RuntimeException {

    private final Set<Currency> currencies;

    public MissingExchangeRateException(Currency currency) {
        this(Set.of(currency));
    }

    public MissingExchangeRateException(Set<Currency> currencies) {
        super("No exchange rate to USD for "
                + currencies.stream()
                        .map(Currency::getCurrencyCode)
                        .sorted()
                        .collect(Collectors.joining(", ")));
        this.currencies = Set.copyOf(currencies);
    }

    /** The currencies that have no rate. */
    public Set<Currency> currencies() {
        return currencies;
    }
}
