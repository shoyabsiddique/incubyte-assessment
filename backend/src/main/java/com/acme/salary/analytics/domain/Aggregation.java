package com.acme.salary.analytics.domain;

import java.util.Optional;

/** How the values of a group are combined. */
public enum Aggregation {
    COUNT("count"),
    SUM("sum"),
    AVERAGE("average"),
    MEDIAN("median"),
    MINIMUM("minimum"),
    MAXIMUM("maximum");

    private final String apiName;

    Aggregation(String apiName) {
        this.apiName = apiName;
    }

    public String apiName() {
        return apiName;
    }

    public static Optional<Aggregation> find(String name) {
        for (Aggregation value : values()) {
            if (value.apiName.equals(name)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /** Headcount can only be counted; every other measure can be combined any way. */
    public boolean appliesTo(Measure measure) {
        return measure != Measure.HEADCOUNT || this == COUNT;
    }
}
