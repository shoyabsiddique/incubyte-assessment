package com.acme.salary.compensation.application;

import com.acme.salary.audit.api.AuditEntityType;
import com.acme.salary.audit.api.AuditEvent;
import com.acme.salary.audit.api.AuditOperation;
import com.acme.salary.audit.api.AuditTrail;
import com.acme.salary.compensation.domain.LatestSalary;
import com.acme.salary.compensation.domain.SalaryRevision;
import com.acme.salary.compensation.persistence.CompensationRecordEntity;
import com.acme.salary.compensation.persistence.CompensationRecordRepository;
import com.acme.salary.shared.domain.Actor;
import com.acme.salary.shared.domain.DomainException;
import com.acme.salary.shared.domain.ErrorKind;
import com.acme.salary.shared.domain.Money;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gives an employee a new salary from a date. One transaction: close the old record, open the new one, audit once. */
@Service
public class SalaryRevisionService {

    private final CompensationRecordRepository records;
    private final AuditTrail audit;

    SalaryRevisionService(CompensationRecordRepository records, AuditTrail audit) {
        this.records = records;
        this.audit = audit;
    }

    /** Returns the id of the new salary record. */
    @Transactional
    public UUID revise(Actor actor, UUID employeeId, String amount, String effectiveStart) {
        CompensationRecordEntity latest = records.findFirstByEmployeeIdOrderByEffectiveStartDesc(employeeId)
                .orElseThrow(() -> new DomainException(ErrorKind.NOT_FOUND, "EMPLOYEE_NOT_FOUND", "There is no employee with this id"));
        Money previous = Money.of(latest.getAmount(), Currency.getInstance(latest.getCurrency()));
        SalaryRevision revision =
                SalaryRevision.revise(new LatestSalary(previous, latest.getEffectiveStart()), amount, effectiveStart);
        Map<String, Object> before = snapshot(previous, latest.getEffectiveStart().toString());

        CompensationRecordEntity created = CompensationRecordEntity.open(
                UUID.randomUUID(),
                employeeId,
                revision.newSalary().amount(),
                latest.getCurrency(),
                revision.newStart());
        latest.closeOn(revision.previousEnd());
        try {
            // The old record is closed first so the one-open-record key never sees two open records.
            records.saveAndFlush(latest);
            records.saveAndFlush(created);
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException raced) {
            // Another revision for this employee committed first; the version or the unique key decided.
            throw new DomainException(
                    ErrorKind.CONFLICT, "REVISION_OVERLAP", "The salary was revised by someone else at the same time; reload and try again");
        }
        audit.record(new AuditEvent(
                actor.userId(),
                actor.email(),
                AuditEntityType.COMPENSATION_RECORD,
                created.getId(),
                employeeId,
                AuditOperation.REVISE,
                before,
                snapshot(revision.newSalary(), revision.newStart().toString())));
        return created.getId();
    }

    private static Map<String, Object> snapshot(Money amount, String effectiveStart) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("amount", amount.displayMap());
        fields.put("effectiveStart", effectiveStart);
        fields.put("effectiveEnd", null);
        return fields;
    }
}
