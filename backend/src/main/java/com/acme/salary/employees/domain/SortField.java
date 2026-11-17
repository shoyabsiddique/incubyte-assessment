package com.acme.salary.employees.domain;

import java.util.Arrays;
import java.util.Optional;

/** The only fields the directory can be sorted by; anything else is refused, never passed to the database. */
public enum SortField {
    EMPLOYEE_ID("employeeId"),
    NAME("name"),
    JOB_TITLE("jobTitle"),
    DEPARTMENT("department"),
    COUNTRY("country"),
    HIRE_DATE("hireDate"),
    STATUS("status"),
    SALARY("salary");

    private final String parameter;

    SortField(String parameter) {
        this.parameter = parameter;
    }

    public String parameter() {
        return parameter;
    }

    static Optional<SortField> fromParameter(String value) {
        return Arrays.stream(values()).filter(field -> field.parameter.equals(value)).findFirst();
    }
}
