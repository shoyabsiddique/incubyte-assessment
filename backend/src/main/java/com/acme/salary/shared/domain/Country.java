package com.acme.salary.shared.domain;

import java.util.Arrays;
import java.util.Currency;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** The countries ACME employs people in, and the one currency each is paid in. */
public enum Country {
    INDIA("India", "INR"),
    USA("United States", "USD"),
    UK("United Kingdom", "GBP"),
    GERMANY("Germany", "EUR"),
    SINGAPORE("Singapore", "SGD"),
    UAE("United Arab Emirates", "AED"),
    CANADA("Canada", "CAD"),
    AUSTRALIA("Australia", "AUD");

    private static final Set<Currency> SUPPORTED_CURRENCIES =
            Arrays.stream(values()).map(Country::currency).collect(Collectors.toUnmodifiableSet());

    private final String displayName;
    private final Currency currency;

    Country(String displayName, String currencyCode) {
        this.displayName = displayName;
        this.currency = Currency.getInstance(currencyCode);
    }

    public String displayName() {
        return displayName;
    }

    public Currency currency() {
        return currency;
    }

    /** Every currency a salary can be stored in. */
    public static Set<Currency> supportedCurrencies() {
        return SUPPORTED_CURRENCIES;
    }

    public static Optional<Country> fromDisplayName(String name) {
        String wanted = name == null ? "" : name.trim();
        return Arrays.stream(values()).filter(c -> c.displayName.equalsIgnoreCase(wanted)).findFirst();
    }
}
