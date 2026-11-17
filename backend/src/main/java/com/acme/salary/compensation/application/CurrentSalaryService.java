package com.acme.salary.compensation.application;

import com.acme.salary.compensation.api.CurrentSalaryQuery;
import com.acme.salary.compensation.api.ExchangeRateQuery;
import com.acme.salary.compensation.persistence.CompensationRecordEntity;
import com.acme.salary.compensation.persistence.CompensationRecordRepository;
import com.acme.salary.shared.domain.ExchangeRates;
import com.acme.salary.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CurrentSalaryService implements CurrentSalaryQuery {

    /** Keeps IN lists within what the database and driver handle comfortably. */
    private static final int CHUNK_SIZE = 1_000;

    private final CompensationRecordRepository records;
    private final ExchangeRateQuery rates;
    private final Clock clock;

    CurrentSalaryService(CompensationRecordRepository records, ExchangeRateQuery rates, Clock clock) {
        this.records = records;
        this.rates = rates;
        this.clock = clock;
    }

    @Override
    public Map<UUID, Money> currentSalaries(Collection<UUID> employeeIds) {
        LocalDate today = LocalDate.now(clock);
        List<UUID> ids = new ArrayList<>(employeeIds);
        Map<UUID, Money> salaries = new HashMap<>();
        for (int from = 0; from < ids.size(); from += CHUNK_SIZE) {
            List<UUID> chunk = ids.subList(from, Math.min(from + CHUNK_SIZE, ids.size()));
            for (CompensationRecordEntity record : records.findCurrent(chunk, today)) {
                salaries.putIfAbsent(
                        record.getEmployeeId(),
                        Money.of(record.getAmount(), Currency.getInstance(record.getCurrency())));
            }
        }
        return salaries;
    }

    @Override
    public List<UUID> rankByCurrentUsd(Collection<UUID> employeeIds, boolean descending) {
        if (employeeIds.isEmpty()) {
            return List.of();
        }
        ExchangeRates snapshot = rates.snapshot();
        Map<UUID, Money> salaries = currentSalaries(employeeIds);
        Map<UUID, BigDecimal> usd = new HashMap<>();
        salaries.forEach((id, salary) -> usd.put(id, snapshot.toUsd(salary).amount()));

        Comparator<BigDecimal> byAmount = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        Comparator<UUID> order = Comparator.comparing(usd::get, Comparator.nullsLast(byAmount));
        List<UUID> ranked = new ArrayList<>(employeeIds);
        ranked.sort(order); // a stable sort, so equal salaries keep the order the caller gave
        return ranked;
    }
}
