package com.acme.salary.analytics.domain;

import com.acme.salary.shared.domain.Money;

/** An employee with their salary converted to USD at the snapshot's rates. */
public record RankedSalary(ActiveSalary person, Money usd) {}
