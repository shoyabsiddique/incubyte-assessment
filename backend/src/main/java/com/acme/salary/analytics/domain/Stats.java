package com.acme.salary.analytics.domain;

import com.acme.salary.shared.domain.Money;

/** Count and spread of a set of salaries, all in one currency and never rounded here. */
public record Stats(long count, Money total, Money average, Money median, Money minimum, Money maximum) {}
