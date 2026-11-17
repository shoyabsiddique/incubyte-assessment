package com.acme.salary.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** The only place money is rounded: half-up to two decimal places, for presentation only. */
public final class Rounding {

    public static final int DISPLAY_SCALE = 2;

    private Rounding() {}

    public static BigDecimal display(BigDecimal amount) {
        return amount.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    public static Money display(Money money) {
        return Money.of(display(money.amount()), money.currency());
    }
}
