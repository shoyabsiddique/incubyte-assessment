package com.acme.salary.seed.application;

public record SeedOutcome(boolean skipped, int employees, int records) {

    static SeedOutcome skippedBecausePopulated() {
        return new SeedOutcome(true, 0, 0);
    }
}
