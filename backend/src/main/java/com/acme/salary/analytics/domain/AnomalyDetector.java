package com.acme.salary.analytics.domain;

import com.acme.salary.shared.domain.ExchangeRates;
import com.acme.salary.shared.domain.Money;
import com.acme.salary.shared.domain.MoneyMath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Flags active employees whose USD salary is strictly more than 20% above or below the median of their
 * Peer Group (country + department + job title, trimmed and compared ignoring case). Groups smaller than
 * five are never flagged: a median of fewer people says too little. A flag is a prompt to review, not a verdict.
 */
public final class AnomalyDetector {

    public static final int MINIMUM_GROUP_SIZE = 5;
    public static final BigDecimal THRESHOLD_PERCENT = BigDecimal.valueOf(20);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal UPPER = BigDecimal.valueOf(120);
    private static final BigDecimal LOWER = BigDecimal.valueOf(80);
    private static final Currency USD = Currency.getInstance("USD");

    private AnomalyDetector() {}

    /** Rows must already be limited to active employees; the result keeps the order of the first appearance. */
    public static List<Anomaly> detect(List<ActiveSalary> rows, ExchangeRates rates) {
        Map<String, List<ActiveSalary>> groups = new LinkedHashMap<>();
        for (ActiveSalary row : rows) {
            groups.computeIfAbsent(peerKey(row), key -> new ArrayList<>()).add(row);
        }
        List<Anomaly> flagged = new ArrayList<>();
        for (List<ActiveSalary> members : groups.values()) {
            if (members.size() < MINIMUM_GROUP_SIZE) {
                continue;
            }
            List<BigDecimal> usdValues =
                    members.stream().map(m -> rates.toUsd(m.salary()).amount()).sorted().toList();
            BigDecimal median = SalaryStatistics.medianOf(usdValues);
            BigDecimal high = median.multiply(UPPER, MoneyMath.CONTEXT).divide(HUNDRED, MoneyMath.CONTEXT);
            BigDecimal low = median.multiply(LOWER, MoneyMath.CONTEXT).divide(HUNDRED, MoneyMath.CONTEXT);
            for (ActiveSalary member : members) {
                BigDecimal usd = rates.toUsd(member.salary()).amount();
                boolean above = usd.compareTo(high) > 0;
                boolean below = usd.compareTo(low) < 0;
                if (above || below) {
                    BigDecimal deviation = usd.subtract(median, MoneyMath.CONTEXT)
                            .multiply(HUNDRED, MoneyMath.CONTEXT)
                            .divide(median, MoneyMath.CONTEXT);
                    flagged.add(new Anomaly(
                            member,
                            Money.of(usd, USD),
                            Money.of(median, USD),
                            members.size(),
                            deviation,
                            above ? Direction.ABOVE : Direction.BELOW));
                }
            }
        }
        return flagged;
    }

    private static String peerKey(ActiveSalary row) {
        return (row.country().name() + "|" + row.department().trim() + "|" + row.jobTitle().trim())
                .toLowerCase(Locale.ROOT);
    }
}
