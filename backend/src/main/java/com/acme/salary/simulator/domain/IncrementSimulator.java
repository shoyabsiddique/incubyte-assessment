package com.acme.salary.simulator.domain;

import com.acme.salary.analytics.api.UsdPay;
import com.acme.salary.shared.domain.Money;
import com.acme.salary.shared.domain.MoneyMath;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

/** What a percentage pay increment would do to annual payroll. Pure arithmetic; it changes nothing. */
public final class IncrementSimulator {

    private static final Currency USD = Currency.getInstance("USD");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private IncrementSimulator() {}

    public static SimulationResult simulate(List<UsdPay> people, BigDecimal percent, SimulationScope scope) {
        List<UsdPay> inScope = people.stream().filter(scope::includes).toList();
        BigDecimal current = inScope.stream()
                .map(pay -> pay.usd().amount())
                .reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MoneyMath.CONTEXT));
        BigDecimal projected = current.multiply(
                BigDecimal.ONE.add(percent.divide(HUNDRED, MoneyMath.CONTEXT), MoneyMath.CONTEXT), MoneyMath.CONTEXT);
        return new SimulationResult(
                inScope.size(),
                Money.of(current, USD),
                Money.of(projected.subtract(current, MoneyMath.CONTEXT), USD),
                Money.of(projected, USD));
    }
}
