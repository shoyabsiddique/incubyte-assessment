package com.acme.salary.compensation.persistence;

import java.math.BigDecimal;
import java.util.UUID;

/** Just the three columns needed to know someone's current pay, without loading whole entities. */
public record CurrentSalaryRow(UUID employeeId, BigDecimal amount, String currency) {}
