package com.acme.salary.analytics.domain;

import java.util.Optional;

/** What salary statistics are grouped by, named as the API names them. */
public enum GroupBy {
    COUNTRY("country"),
    DEPARTMENT("department"),
    JOB_TITLE("jobTitle");

    private final String apiName;

    GroupBy(String apiName) {
        this.apiName = apiName;
    }

    public static GroupBy parse(String name) {
        return find(name).orElseThrow(() -> new IllegalArgumentException("Unknown grouping: " + name));
    }

    public static Optional<GroupBy> find(String name) {
        for (GroupBy value : values()) {
            if (value.apiName.equals(name)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    String keyOf(ActiveSalary row) {
        return switch (this) {
            case COUNTRY -> row.country().name();
            case DEPARTMENT -> row.department();
            case JOB_TITLE -> row.jobTitle();
        };
    }
}
