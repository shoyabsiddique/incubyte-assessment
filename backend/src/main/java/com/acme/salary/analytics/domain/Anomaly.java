package com.acme.salary.analytics.domain;

import com.acme.salary.shared.domain.Money;
import java.math.BigDecimal;

/**
 * A salary that sits far from its peer group median, for HR to review.
 *
 * @param deviationPercent signed: positive above the median, negative below it
 */
public record Anomaly(
        ActiveSalary person, Money usd, Money median, int groupSize, BigDecimal deviationPercent, Direction direction) {}
