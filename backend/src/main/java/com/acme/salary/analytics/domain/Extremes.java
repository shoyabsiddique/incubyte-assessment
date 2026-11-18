package com.acme.salary.analytics.domain;

import java.util.List;

/** The best-paid people (highest first) and the worst-paid (lowest first); nobody is in both lists. */
public record Extremes(List<RankedSalary> highest, List<RankedSalary> lowest) {}
