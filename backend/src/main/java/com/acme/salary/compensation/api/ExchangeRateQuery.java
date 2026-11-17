package com.acme.salary.compensation.api;

import com.acme.salary.shared.domain.ExchangeRates;

/** How other modules obtain the current exchange rates. */
public interface ExchangeRateQuery {

    /** The current rates as one consistent snapshot; load it once per request. */
    ExchangeRates snapshot();
}
