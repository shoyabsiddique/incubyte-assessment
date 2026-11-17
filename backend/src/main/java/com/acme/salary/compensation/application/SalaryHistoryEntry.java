package com.acme.salary.compensation.application;

import com.acme.salary.shared.domain.Money;
import java.time.LocalDate;
import java.util.UUID;

/** One salary period of an employee's history, with its USD equivalent at current rates. */
public record SalaryHistoryEntry(
        UUID id, Money amount, Money amountUsd, LocalDate effectiveStart, LocalDate effectiveEnd, boolean current) {}
