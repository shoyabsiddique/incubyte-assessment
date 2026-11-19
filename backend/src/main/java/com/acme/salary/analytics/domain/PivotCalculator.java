package com.acme.salary.analytics.domain;

import com.acme.salary.shared.domain.MoneyMath;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Groups rows by one or two fields and aggregates one measure: the engine behind every dashboard chart. */
public final class PivotCalculator {

    private static final String ALL = "all";

    private PivotCalculator() {}

    public static PivotResult pivot(
            List<DashboardRow> rows,
            Dimension x,
            Dimension seriesBy,
            Measure measure,
            Aggregation aggregation,
            LocalDate today) {
        TreeSet<String> categories = new TreeSet<>();
        TreeMap<String, Map<String, List<BigDecimal>>> cells = new TreeMap<>();
        for (DashboardRow row : rows) {
            String category = x.keyOf(row);
            String series = seriesBy == null ? ALL : seriesBy.keyOf(row);
            categories.add(category);
            cells.computeIfAbsent(series, s -> new TreeMap<>())
                    .computeIfAbsent(category, c -> new ArrayList<>())
                    .add(measure.valueOf(row, today));
        }
        List<PivotSeries> result = new ArrayList<>();
        cells.forEach((series, byCategory) -> {
            List<PivotPoint> points = new ArrayList<>();
            for (String category : categories) {
                List<BigDecimal> values = byCategory.get(category);
                points.add(
                        values == null
                                ? new PivotPoint(category, BigDecimal.ZERO, 0)
                                : new PivotPoint(category, combine(values, aggregation), values.size()));
            }
            result.add(new PivotSeries(series, points));
        });
        return new PivotResult(List.copyOf(categories), result);
    }

    private static BigDecimal combine(List<BigDecimal> values, Aggregation aggregation) {
        List<BigDecimal> sorted = values.stream().sorted().toList();
        BigDecimal sum = sorted.stream().reduce(BigDecimal.ZERO, (a, b) -> a.add(b, MoneyMath.CONTEXT));
        return switch (aggregation) {
            case COUNT -> BigDecimal.valueOf(values.size());
            case SUM -> sum;
            case AVERAGE -> sum.divide(BigDecimal.valueOf(values.size()), MoneyMath.CONTEXT);
            case MEDIAN -> SalaryStatistics.medianOf(sorted);
            case MINIMUM -> sorted.get(0);
            case MAXIMUM -> sorted.get(sorted.size() - 1);
        };
    }
}
