package com.acme.salary.compensation.application;

import com.acme.salary.compensation.api.ExchangeRateQuery;
import com.acme.salary.compensation.persistence.ExchangeRateEntity;
import com.acme.salary.compensation.persistence.ExchangeRateRepository;
import com.acme.salary.shared.domain.ExchangeRates;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ExchangeRateService implements ExchangeRateQuery {

    private final ExchangeRateRepository repository;

    ExchangeRateService(ExchangeRateRepository repository) {
        this.repository = repository;
    }

    @Override
    public ExchangeRates snapshot() {
        Map<Currency, BigDecimal> rates = new HashMap<>();
        for (ExchangeRateEntity entity : repository.findAll()) {
            rates.put(Currency.getInstance(entity.getCurrency()), entity.getRateToUsd());
        }
        return ExchangeRates.of(rates);
    }
}
