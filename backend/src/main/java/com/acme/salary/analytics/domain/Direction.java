package com.acme.salary.analytics.domain;

import java.util.Optional;

/** Whether a salary is above or below its peer group median. */
public enum Direction {
    ABOVE,
    BELOW;

    public static Optional<Direction> find(String apiName) {
        if ("above".equals(apiName)) {
            return Optional.of(ABOVE);
        }
        if ("below".equals(apiName)) {
            return Optional.of(BELOW);
        }
        return Optional.empty();
    }
}
