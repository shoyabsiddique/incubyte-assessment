package com.acme.salary.analytics.domain;

import java.util.Optional;

/** A field the dashboard can group by, named as the API names it. */
public enum Dimension {
    COUNTRY("country"),
    DEPARTMENT("department"),
    JOB_TITLE("jobTitle"),
    CITY("city"),
    EMPLOYMENT_TYPE("employmentType"),
    HIRE_YEAR("hireYear");

    private final String apiName;

    Dimension(String apiName) {
        this.apiName = apiName;
    }

    public String apiName() {
        return apiName;
    }

    public static Optional<Dimension> find(String name) {
        for (Dimension value : values()) {
            if (value.apiName.equals(name)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    String keyOf(DashboardRow row) {
        return switch (this) {
            case COUNTRY -> row.country().name();
            case DEPARTMENT -> row.department();
            case JOB_TITLE -> row.jobTitle();
            case CITY -> row.city();
            case EMPLOYMENT_TYPE -> row.employmentType();
            case HIRE_YEAR -> String.valueOf(row.hireDate().getYear());
        };
    }
}
