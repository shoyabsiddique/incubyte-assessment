package com.acme.salary.compensation.application;

import com.acme.salary.compensation.api.ExchangeRateQuery;
import com.acme.salary.compensation.domain.SalaryPeriod;
import com.acme.salary.compensation.persistence.CompensationRecordEntity;
import com.acme.salary.compensation.persistence.CompensationRecordRepository;
import com.acme.salary.shared.domain.DomainException;
import com.acme.salary.shared.domain.ErrorKind;
import com.acme.salary.shared.domain.ExchangeRates;
import com.acme.salary.shared.domain.Money;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SalaryHistoryService {

    private final CompensationRecordRepository records;
    private final ExchangeRateQuery rates;
    private final Clock clock;

    SalaryHistoryService(CompensationRecordRepository records, ExchangeRateQuery rates, Clock clock) {
        this.records = records;
        this.rates = rates;
        this.clock = clock;
    }

    /** Every salary period, newest first. Compensation knows only records, so no records means no history. */
    public List<SalaryHistoryEntry> historyOf(UUID employeeId) {
        List<CompensationRecordEntity> found = records.findByEmployeeIdOrderByEffectiveStartDesc(employeeId);
        if (found.isEmpty()) {
            throw new DomainException(ErrorKind.NOT_FOUND, "SALARY_HISTORY_NOT_FOUND", "No salary history for this employee");
        }
        ExchangeRates snapshot = rates.snapshot();
        LocalDate today = LocalDate.now(clock);
        boolean currentFound = false;
        List<SalaryHistoryEntry> history = new ArrayList<>(found.size());
        for (CompensationRecordEntity record : found) {
            boolean covers = new SalaryPeriod(record.getEffectiveStart(), record.getEffectiveEnd()).covers(today);
            boolean current = covers && !currentFound; // the newest period wins if periods ever overlap
            currentFound = currentFound || current;
            Money amount = Money.of(record.getAmount(), Currency.getInstance(record.getCurrency()));
            history.add(new SalaryHistoryEntry(
                    record.getId(), amount, snapshot.toUsd(amount), record.getEffectiveStart(), record.getEffectiveEnd(), current));
        }
        return history;
    }
}
