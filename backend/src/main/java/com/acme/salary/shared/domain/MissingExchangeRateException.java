package com.acme.salary.shared.domain;

import java.util.Currency;

/** Thrown when a conversion needs a rate that is not in the snapshot. Never skipped silently. */
public class MissingExchangeRateException extends RuntimeException {

    public MissingExchangeRateException(Currency currency) {
        super("No exchange rate to USD for " + currency.getCurrencyCode());
    }
}
