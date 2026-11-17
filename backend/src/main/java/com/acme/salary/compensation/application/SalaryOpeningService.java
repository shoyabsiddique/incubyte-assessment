package com.acme.salary.compensation.application;

import com.acme.salary.audit.api.AuditedByCaller;
import com.acme.salary.compensation.api.SalaryOpening;
import com.acme.salary.compensation.persistence.CompensationRecordEntity;
import com.acme.salary.compensation.persistence.CompensationRecordRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Part of adding an employee; the employee creation records the one audit entry for the whole operation. */
@Service
@AuditedByCaller
class SalaryOpeningService implements SalaryOpening {

    private final CompensationRecordRepository records;

    SalaryOpeningService(CompensationRecordRepository records) {
        this.records = records;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void open(UUID employeeId, BigDecimal amount, Currency currency, LocalDate effectiveStart) {
        records.save(CompensationRecordEntity.open(
                UUID.randomUUID(), employeeId, amount, currency.getCurrencyCode(), effectiveStart));
    }
}
