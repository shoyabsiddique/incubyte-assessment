package com.acme.salary.analytics.api;

import com.acme.salary.shared.domain.Country;
import com.acme.salary.shared.domain.Money;

/** What one active employee is paid today, converted to USD at one exchange-rate snapshot. */
public record UsdPay(Country country, String department, Money usd) {}
