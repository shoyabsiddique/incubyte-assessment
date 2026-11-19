package com.acme.salary.analytics.domain;

import java.math.BigDecimal;

/** One cell of a pivot: the aggregated value for a category and series, and how many people it covers. */
public record PivotPoint(String category, BigDecimal value, long count) {}
