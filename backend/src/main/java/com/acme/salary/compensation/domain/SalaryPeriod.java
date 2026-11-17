package com.acme.salary.compensation.domain;

import java.time.LocalDate;

/** The days a salary applies for. A null end means it is still in force. */
public record SalaryPeriod(LocalDate start, LocalDate end) {

    public boolean isOpen() {
        return end == null;
    }

    public boolean covers(LocalDate day) {
        return !day.isBefore(start) && (end == null || !day.isAfter(end));
    }
}
