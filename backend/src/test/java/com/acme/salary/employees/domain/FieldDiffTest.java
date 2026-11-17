package com.acme.salary.employees.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FieldDiffTest {

    @Test
    void keepsOnlyTheFieldsThatChanged() {
        FieldDiff diff = FieldDiff.between(
                Map.of("jobTitle", "Engineer", "city", "Sydney", "status", "ACTIVE"),
                Map.of("jobTitle", "Senior Engineer", "city", "Sydney", "status", "ON_LEAVE"));

        assertThat(diff.before()).containsOnly(Map.entry("jobTitle", "Engineer"), Map.entry("status", "ACTIVE"));
        assertThat(diff.after()).containsOnly(Map.entry("jobTitle", "Senior Engineer"), Map.entry("status", "ON_LEAVE"));
    }

    @Test
    void noChangeIsEmpty() {
        FieldDiff diff = FieldDiff.between(Map.of("city", "Sydney"), Map.of("city", "Sydney"));

        assertThat(diff.isEmpty()).isTrue();
    }
}
