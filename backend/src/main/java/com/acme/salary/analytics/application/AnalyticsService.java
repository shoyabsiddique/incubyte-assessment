package com.acme.salary.analytics.application;

import com.acme.salary.analytics.domain.ActiveSalary;
import com.acme.salary.analytics.domain.Aggregation;
import com.acme.salary.analytics.domain.Anomaly;
import com.acme.salary.analytics.domain.AnomalyDetector;
import com.acme.salary.analytics.domain.DashboardRow;
import com.acme.salary.analytics.domain.Dimension;
import com.acme.salary.analytics.domain.Direction;
import com.acme.salary.analytics.domain.Measure;
import com.acme.salary.analytics.domain.PivotCalculator;
import com.acme.salary.analytics.domain.PivotResult;
import com.acme.salary.analytics.domain.Extremes;
import com.acme.salary.analytics.domain.GroupBy;
import com.acme.salary.analytics.domain.GroupStats;
import com.acme.salary.analytics.domain.PayExtremes;
import com.acme.salary.analytics.domain.SalaryStatistics;
import com.acme.salary.analytics.persistence.ActiveCompensationStore;
import com.acme.salary.compensation.api.ExchangeRateQuery;
import com.acme.salary.shared.domain.Country;
import com.acme.salary.shared.domain.DomainException;
import com.acme.salary.shared.domain.ErrorKind;
import com.acme.salary.shared.domain.ExchangeRates;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pay analytics for active employees, always from one view, one rate snapshot and one date. */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    public static final int DEFAULT_N = 5;
    public static final int MAX_N = 25;
    public static final int MAX_PAGE_SIZE = 100;

    private final ActiveCompensationStore store;
    private final ExchangeRateQuery rates;
    private final Clock clock;

    AnalyticsService(ActiveCompensationStore store, ExchangeRateQuery rates, Clock clock) {
        this.store = store;
        this.rates = rates;
        this.clock = clock;
    }

    public SalaryStatsResult salaryStats(String groupBy, String country, String department, String jobTitle) {
        GroupBy grouping = groupBy == null || groupBy.isBlank()
                ? GroupBy.COUNTRY
                : GroupBy.find(groupBy.trim())
                        .orElseThrow(() -> invalid("INVALID_GROUP_BY", "groupBy must be country, department or jobTitle"));
        Optional<Country> countryFilter = country(country);
        Optional<String> departmentFilter = known(department, "department", store.departments());
        Optional<String> titleFilter = known(jobTitle, "jobTitle", store.jobTitles());
        List<ActiveSalary> rows = store.findInForce(today(), countryFilter, departmentFilter, titleFilter);
        ExchangeRates snapshot = rates.snapshot();
        List<GroupStats> groups = SalaryStatistics.calculate(rows, grouping, snapshot, countryFilter.isPresent());
        return new SalaryStatsResult(grouping, countryFilter.isPresent(), groups);
    }

    /** One aggregated measure grouped by one or two fields, for a dashboard chart. */
    public PivotResult pivot(
            String x,
            String series,
            String measure,
            String aggregation,
            String country,
            String department,
            String jobTitle) {
        Dimension xDimension = Dimension.find(x == null ? "" : x.trim())
                .orElseThrow(() -> invalid(
                        "INVALID_DIMENSION", "x must be country, department, jobTitle, city, employmentType or hireYear"));
        Dimension seriesDimension = series == null || series.isBlank()
                ? null
                : Dimension.find(series.trim())
                        .orElseThrow(() -> invalid("INVALID_DIMENSION", "series must be a field to group by, or left out"));
        Measure measureValue = measure == null || measure.isBlank()
                ? Measure.HEADCOUNT
                : Measure.find(measure.trim())
                        .orElseThrow(() -> invalid("INVALID_MEASURE", "measure must be headcount, salaryUsd or tenureYears"));
        Aggregation aggregationValue = aggregation == null || aggregation.isBlank()
                ? (measureValue == Measure.HEADCOUNT ? Aggregation.COUNT : Aggregation.AVERAGE)
                : Aggregation.find(aggregation.trim())
                        .orElseThrow(() -> invalid(
                                "INVALID_AGGREGATION", "aggregation must be count, sum, average, median, minimum or maximum"));
        if (!aggregationValue.appliesTo(measureValue)) {
            throw invalid("INVALID_AGGREGATION", "headcount can only be counted");
        }
        if (seriesDimension == xDimension) {
            throw invalid("INVALID_DIMENSION", "series must differ from x");
        }
        Optional<Country> countryFilter = country(country);
        Optional<String> departmentFilter = known(department, "department", store.departments());
        Optional<String> titleFilter = known(jobTitle, "jobTitle", store.jobTitles());
        LocalDate today = today();
        List<DashboardRow> rows =
                store.findDashboardRows(today, countryFilter, departmentFilter, titleFilter, rates.snapshot());
        return PivotCalculator.pivot(rows, xDimension, seriesDimension, measureValue, aggregationValue, today);
    }

    public List<DepartmentExtremes> payExtremes(String department, String n) {
        int size = parseN(n);
        Optional<String> departmentFilter = known(department, "department", store.departments());
        List<ActiveSalary> rows = store.findInForce(today(), Optional.empty(), departmentFilter, Optional.empty());
        ExchangeRates snapshot = rates.snapshot();
        Map<String, List<ActiveSalary>> byDepartment = new TreeMap<>();
        rows.forEach(row -> byDepartment.computeIfAbsent(row.department(), d -> new ArrayList<>()).add(row));
        List<DepartmentExtremes> result = new ArrayList<>();
        byDepartment.forEach((name, members) -> {
            Extremes extremes = PayExtremes.of(members, size, snapshot);
            result.add(new DepartmentExtremes(name, extremes));
        });
        return result;
    }

    /** Potential anomalies for HR review. Peer group medians use everyone; the filters only narrow what is listed. */
    public AnomalyPage anomalies(
            String country, String department, String direction, String page, String size, String sort) {
        int pageNumber = number(page, 0, 0, 1_000_000, "INVALID_PAGE", "INVALID_PAGE");
        int pageSize = number(size, 20, 1, MAX_PAGE_SIZE, "INVALID_PAGE", "PAGE_SIZE_TOO_LARGE");
        Comparator<Anomaly> order = order(sort);
        Optional<Country> countryFilter = country(country);
        Optional<String> departmentFilter = known(department, "department", store.departments());
        Optional<Direction> directionFilter = direction == null || direction.isBlank()
                ? Optional.empty()
                : Optional.of(Direction.find(direction.trim())
                        .orElseThrow(() -> invalid("INVALID_FILTER", "direction must be above or below")));
        List<ActiveSalary> rows = store.findInForce(today(), Optional.empty(), Optional.empty(), Optional.empty());
        List<Anomaly> matching = AnomalyDetector.detect(rows, rates.snapshot()).stream()
                .filter(a -> countryFilter.map(c -> a.person().country() == c).orElse(true))
                .filter(a -> departmentFilter.map(d -> a.person().department().equals(d)).orElse(true))
                .filter(a -> directionFilter.map(d -> a.direction() == d).orElse(true))
                .sorted(order)
                .toList();
        int from = (int) Math.min((long) pageNumber * pageSize, matching.size());
        int to = Math.min(from + pageSize, matching.size());
        int pages = (matching.size() + pageSize - 1) / pageSize;
        return new AnomalyPage(matching.subList(from, to), pageNumber, pageSize, matching.size(), pages);
    }

    private static Comparator<Anomaly> order(String sort) {
        String field = "magnitude";
        boolean descending = true;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.trim().split(",", -1);
            field = parts[0];
            if (parts.length > 2 || (parts.length == 2 && !parts[1].equals("asc") && !parts[1].equals("desc"))) {
                throw invalid("INVALID_SORT", "sort must be a field, optionally followed by ,asc or ,desc");
            }
            descending = parts.length == 2 ? parts[1].equals("desc") : !field.equals("name") && !field.equals("employeeId");
        }
        Comparator<Anomaly> base =
                switch (field) {
                    case "magnitude" -> Comparator.comparing((Anomaly a) -> a.deviationPercent().abs());
                    case "deviation" -> Comparator.comparing(Anomaly::deviationPercent);
                    case "employeeId" -> Comparator.comparing((Anomaly a) -> a.person().employeeId());
                    case "name" -> Comparator.comparing((Anomaly a) -> a.person().name().toLowerCase());
                    case "department" -> Comparator.comparing((Anomaly a) -> a.person().department().toLowerCase());
                    case "country" -> Comparator.comparing((Anomaly a) -> a.person().country().displayName());
                    case "salary" -> Comparator.comparing((Anomaly a) -> a.usd().amount());
                    default -> throw invalid(
                            "INVALID_SORT",
                            "sort must be one of magnitude, deviation, employeeId, name, department, country or salary");
                };
        Comparator<Anomaly> directed = descending ? base.reversed() : base;
        return directed.thenComparing(a -> a.person().employeeId());
    }

    private static int number(String text, int fallback, int min, int max, String badCode, String tooLargeCode) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        int value;
        try {
            value = Integer.parseInt(text.trim());
        } catch (NumberFormatException notANumber) {
            throw invalid(badCode, "page and size must be whole numbers");
        }
        if (value < min) {
            throw invalid(badCode, "page and size are out of range");
        }
        if (value > max) {
            throw invalid(tooLargeCode, "page or size is too large");
        }
        return value;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static int parseN(String text) {
        if (text == null || text.isBlank()) {
            return DEFAULT_N;
        }
        int n;
        try {
            n = Integer.parseInt(text.trim());
        } catch (NumberFormatException notANumber) {
            throw invalid("INVALID_N", "n must be a whole number");
        }
        if (n < 1) {
            throw invalid("INVALID_N", "n must be at least 1");
        }
        if (n > MAX_N) {
            throw invalid("N_TOO_LARGE", "n can be at most " + MAX_N);
        }
        return n;
    }

    private static Optional<Country> country(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Country.valueOf(text.trim()));
        } catch (IllegalArgumentException unknown) {
            throw invalid("INVALID_FILTER", "country is not one of the supported countries");
        }
    }

    private static Optional<String> known(String text, String name, java.util.Set<String> values) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String value = text.trim();
        if (!values.contains(value)) {
            throw invalid("INVALID_FILTER", name + " is not a known value");
        }
        return Optional.of(value);
    }

    private static DomainException invalid(String code, String message) {
        return new DomainException(ErrorKind.INVALID_INPUT, code, message);
    }
}
