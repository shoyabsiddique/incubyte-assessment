package com.acme.salary.employees.application;

import com.acme.salary.audit.api.AuditEntityType;
import com.acme.salary.audit.api.AuditEvent;
import com.acme.salary.audit.api.AuditOperation;
import com.acme.salary.audit.api.AuditTrail;
import com.acme.salary.compensation.api.SalaryOpening;
import com.acme.salary.employees.domain.NewEmployee;
import com.acme.salary.employees.persistence.EmployeeEntity;
import com.acme.salary.employees.persistence.EmployeeIdAllocator;
import com.acme.salary.employees.persistence.EmployeeRepository;
import com.acme.salary.shared.domain.Actor;
import com.acme.salary.shared.domain.DomainException;
import com.acme.salary.shared.domain.ErrorKind;
import com.acme.salary.shared.domain.Money;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Changes to employees. Each public method is one transaction that also writes its single audit entry. */
@Service
public class EmployeeCommandService {

    private final EmployeeRepository employees;
    private final EmployeeIdAllocator ids;
    private final SalaryOpening salaryOpening;
    private final AuditTrail audit;

    EmployeeCommandService(
            EmployeeRepository employees, EmployeeIdAllocator ids, SalaryOpening salaryOpening, AuditTrail audit) {
        this.employees = employees;
        this.ids = ids;
        this.salaryOpening = salaryOpening;
        this.audit = audit;
    }

    /** Adds the person with an open starting salary; returns the id of the new employee. */
    @Transactional
    public UUID create(Actor actor, NewEmployee newEmployee) {
        if (employees.existsByWorkEmailIgnoreCase(newEmployee.profile().workEmail())) {
            throw duplicateEmail();
        }
        EmployeeEntity employee = EmployeeEntity.create(UUID.randomUUID(), ids.next(), newEmployee.profile());
        saveUnique(employee);
        salaryOpening.open(
                employee.getId(),
                newEmployee.salaryAmount(),
                newEmployee.salaryCurrency(),
                newEmployee.salaryEffectiveStart());
        Money salary = Money.of(newEmployee.salaryAmount(), newEmployee.salaryCurrency());
        audit.record(new AuditEvent(
                actor.userId(),
                actor.email(),
                AuditEntityType.EMPLOYEE,
                employee.getId(),
                employee.getId(),
                AuditOperation.CREATE,
                null,
                EmployeeAuditFields.of(employee, salary.displayMap(), newEmployee.salaryEffectiveStart().toString())));
        return employee.getId();
    }

    private void saveUnique(EmployeeEntity employee) {
        try {
            employees.saveAndFlush(employee);
        } catch (DataIntegrityViolationException raced) {
            // Two requests with the same email passed the check together; the unique key decides.
            throw duplicateEmail();
        }
    }

    static DomainException duplicateEmail() {
        return new DomainException(ErrorKind.CONFLICT, "DUPLICATE_EMPLOYEE_EMAIL", "An employee with this work email already exists");
    }
}
