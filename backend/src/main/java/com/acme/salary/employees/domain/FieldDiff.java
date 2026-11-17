package com.acme.salary.employees.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** What changed between two snapshots of the same record: only the fields whose value differs. */
public record FieldDiff(Map<String, Object> before, Map<String, Object> after) {

    public static FieldDiff between(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changedBefore = new LinkedHashMap<>();
        Map<String, Object> changedAfter = new LinkedHashMap<>();
        after.forEach((field, value) -> {
            if (!Objects.equals(before.get(field), value)) {
                changedBefore.put(field, before.get(field));
                changedAfter.put(field, value);
            }
        });
        return new FieldDiff(changedBefore, changedAfter);
    }

    public boolean isEmpty() {
        return after.isEmpty();
    }
}
