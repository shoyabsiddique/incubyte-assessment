package com.acme.salary.analytics.domain;

import com.acme.salary.shared.domain.ExchangeRates;
import com.acme.salary.shared.domain.Money;
import com.acme.salary.shared.domain.MoneyMath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pay statistics per group. Every figure is computed from the rows themselves, never from other statistics. */
public final class SalaryStatistics {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private SalaryStatistics() {}

    /**
     * @param withLocal also compute each group in its own currency; only meaningful when every row of a
     *     group is paid in one currency (a single country), otherwise the local statistics are left out
     */
    public static List<GroupStats> calculate(
            List<ActiveSalary> rows, GroupBy groupBy, ExchangeRates rates, boolean withLocal) {
        Map<String, List<ActiveSalary>> groups = new TreeMap<>();
        for (ActiveSalary row : rows) {
            groups.computeIfAbsent(groupBy.keyOf(row), key -> new ArrayList<>()).add(row);
        }
        List<GroupStats> result = new ArrayList<>(groups.size());
        groups.forEach((key, members) -> {
            Currency usd = Currency.getInstance("USD");
            Stats inUsd = stats(members.stream().map(m -> rates.toUsd(m.salary()).amount()).toList(), usd);
            Stats local = null;
            if (withLocal) {
                Currency currency = members.get(0).salary().currency();
                boolean oneCurrency = members.stream().allMatch(m -> m.salary().currency().equals(currency));
                if (oneCurrency) {
                    local = stats(members.stream().map(m -> m.salary().amount()).toList(), currency);
                }
            }
            result.add(new GroupStats(key, inUsd, local));
        });
        return result;
    }

    private static Stats stats(List<BigDecimal> amounts, Currency currency) {
        List<BigDecimal> sorted = amounts.stream().sorted().toList();
        int size = sorted.size();
        BigDecimal total = sorted.stream().reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MoneyMath.CONTEXT));
        BigDecimal median = size % 2 == 1
                ? sorted.get(size / 2)
                : sorted.get(size / 2 - 1).add(sorted.get(size / 2), MoneyMath.CONTEXT).divide(TWO, MoneyMath.CONTEXT);
        return new Stats(
                size,
                Money.of(total, currency),
                Money.of(total.divide(BigDecimal.valueOf(size), MoneyMath.CONTEXT), currency),
                Money.of(median, currency),
                Money.of(sorted.get(0), currency),
                Money.of(sorted.get(size - 1), currency));
    }
}
