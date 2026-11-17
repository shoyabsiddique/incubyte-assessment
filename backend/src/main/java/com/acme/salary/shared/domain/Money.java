package com.acme.salary.shared.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount in one currency. Arithmetic keeps up to 34 significant digits
 * ({@link MoneyMath#CONTEXT}) and never rounds to cents, so a value is only rounded to 2 decimal
 * places when presented, through {@link Rounding}.
 */
public final class Money implements Comparable<Money> {

    private final BigDecimal amount;
    private final Currency currency;

    private Money(BigDecimal amount, Currency currency) {
        this.amount = Objects.requireNonNull(amount, "amount");
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money of(String amount, Currency currency) {
        return new Money(new BigDecimal(amount), currency);
    }

    public BigDecimal amount() {
        return amount;
    }

    public Currency currency() {
        return currency;
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount, MoneyMath.CONTEXT), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount, MoneyMath.CONTEXT), currency);
    }

    public Money times(BigDecimal factor) {
        return new Money(amount.multiply(factor, MoneyMath.CONTEXT), currency);
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot combine " + currency + " with " + other.currency);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Money m && currency.equals(m.currency) && amount.compareTo(m.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
