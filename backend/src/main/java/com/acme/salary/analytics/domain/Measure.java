package com.acme.salary.analytics.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/** The number the dashboard aggregates: headcount, salary in USD or tenure in years. */
public enum Measure {
    HEADCOUNT("headcount"),
    SALARY_USD("salaryUsd"),
    TENURE_YEARS("tenureYears");

    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365.25");

    private final String apiName;

    Measure(String apiName) {
        this.apiName = apiName;
    }

    public String apiName() {
        return apiName;
    }

    public static Optional<Measure> find(String name) {
        for (Measure value : values()) {
            if (value.apiName.equals(name)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    BigDecimal valueOf(DashboardRow row, LocalDate today) {
        return switch (this) {
            case HEADCOUNT -> BigDecimal.ONE;
            case SALARY_USD -> row.salaryUsd();
            case TENURE_YEARS -> BigDecimal.valueOf(ChronoUnit.DAYS.between(row.hireDate(), today))
                    .divide(DAYS_PER_YEAR, 6, RoundingMode.HALF_UP);
        };
    }
}
