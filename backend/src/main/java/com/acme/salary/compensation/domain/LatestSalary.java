package com.acme.salary.compensation.domain;

import com.acme.salary.shared.domain.Money;
import java.time.LocalDate;

/** The newest salary record of an employee: what a revision replaces, and where it started. */
public record LatestSalary(Money amount, LocalDate start) {}
