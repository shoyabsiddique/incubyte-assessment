package com.acme.salary.compensation.application;

import java.math.BigDecimal;

/** One exchange rate to USD as the HR Manager sees it; USD itself is fixed and not editable. */
public record ExchangeRateView(String currency, BigDecimal rate, long version, boolean editable) {}
