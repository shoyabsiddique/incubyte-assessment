package com.acme.salary.analytics.domain;

import com.acme.salary.shared.domain.ExchangeRates;
import java.util.Comparator;
import java.util.List;

/** Picks the highest- and lowest-paid people by USD equivalent; ties are settled by Employee ID. */
public final class PayExtremes {

    private static final Comparator<RankedSalary> BY_ID = Comparator.comparing(r -> r.person().employeeId());
    private static final Comparator<RankedSalary> HIGH_FIRST =
            Comparator.comparing((RankedSalary r) -> r.usd().amount()).reversed().thenComparing(BY_ID);
    private static final Comparator<RankedSalary> LOW_FIRST =
            Comparator.comparing((RankedSalary r) -> r.usd().amount()).thenComparing(BY_ID);

    private PayExtremes() {}

    public static Extremes of(List<ActiveSalary> rows, int n, ExchangeRates rates) {
        List<RankedSalary> ranked =
                rows.stream().map(row -> new RankedSalary(row, rates.toUsd(row.salary()))).toList();
        List<RankedSalary> byHigh = ranked.stream().sorted(HIGH_FIRST).toList();
        List<RankedSalary> highest = byHigh.stream().limit(n).toList();
        // The lowest are chosen from the people not already listed as highest, so the lists never overlap.
        List<RankedSalary> lowest = byHigh.stream().skip(highest.size()).sorted(LOW_FIRST).limit(n).toList();
        return new Extremes(highest, lowest);
    }
}
