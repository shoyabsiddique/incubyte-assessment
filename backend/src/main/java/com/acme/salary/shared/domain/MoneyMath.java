package com.acme.salary.shared.domain;

import java.math.MathContext;

/** The one arithmetic context for money: 34 significant digits, never rounded to cents. */
public final class MoneyMath {

    public static final MathContext CONTEXT = MathContext.DECIMAL128;

    private MoneyMath() {}
}
