package com.acme.salary.seed.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

/** One generated salary period; a null end date marks the open (current) record. */
public record SeedCompensation(
        UUID id, BigDecimal amount, Currency currency, LocalDate effectiveStart, LocalDate effectiveEnd) {}
