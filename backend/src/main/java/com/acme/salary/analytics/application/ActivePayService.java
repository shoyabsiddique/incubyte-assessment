package com.acme.salary.analytics.application;

import com.acme.salary.analytics.api.ActivePayQuery;
import com.acme.salary.analytics.api.UsdPay;
import com.acme.salary.analytics.persistence.ActiveCompensationStore;
import com.acme.salary.compensation.api.ExchangeRateQuery;
import com.acme.salary.shared.domain.ExchangeRates;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serves the analytics projection to other modules, in USD. */
@Service
@Transactional(readOnly = true)
class ActivePayService implements ActivePayQuery {

    private final ActiveCompensationStore store;
    private final ExchangeRateQuery rates;
    private final Clock clock;

    ActivePayService(ActiveCompensationStore store, ExchangeRateQuery rates, Clock clock) {
        this.store = store;
        this.rates = rates;
        this.clock = clock;
    }

    @Override
    public List<UsdPay> activePay() {
        ExchangeRates snapshot = rates.snapshot();
        return store.findInForce(LocalDate.now(clock), Optional.empty(), Optional.empty(), Optional.empty()).stream()
                .map(row -> new UsdPay(row.country(), row.department(), snapshot.toUsd(row.salary())))
                .toList();
    }
}
