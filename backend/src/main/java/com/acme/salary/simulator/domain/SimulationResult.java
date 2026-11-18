package com.acme.salary.simulator.domain;

import com.acme.salary.shared.domain.Money;

/** The payroll impact of an increment, in USD and not rounded; rounding is for presentation only. */
public record SimulationResult(long employees, Money current, Money additional, Money projected) {}
