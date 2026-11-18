package com.acme.salary.analytics.application;

import com.acme.salary.analytics.domain.ActiveSalary;
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
