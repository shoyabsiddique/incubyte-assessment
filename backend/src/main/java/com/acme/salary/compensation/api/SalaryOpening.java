package com.acme.salary.compensation.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

/** Gives a newly added employee their first, open salary record. Must run inside the caller's transaction. */
public interface SalaryOpening {

    void open(UUID employeeId, BigDecimal amount, Currency currency, LocalDate effectiveStart);
}
