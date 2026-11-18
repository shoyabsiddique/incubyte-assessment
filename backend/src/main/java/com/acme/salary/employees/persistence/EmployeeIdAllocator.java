package com.acme.salary.employees.persistence;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allocates the next Employee ID. The sequence row stays locked until the caller's transaction ends, so
 * concurrent additions queue up instead of colliding. IDs already present (the seeded workforce) are respected.
 */
@Component
public class EmployeeIdAllocator {

    private static final String PREFIX = "EMP-";

    private final EmployeeIdSequenceRepository sequence;
    private final EmployeeRepository employees;

    EmployeeIdAllocator(EmployeeIdSequenceRepository sequence, EmployeeRepository employees) {
        this.sequence = sequence;
        this.employees = employees;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next() {
        EmployeeIdSequenceEntity row = sequence.lockTheSequence();
        // IDs are zero-padded, so the highest one in text order is also the highest number.
        int highestUsed = employees.findFirstByOrderByEmployeeIdDesc()
                .map(e -> Integer.parseInt(e.getEmployeeId().substring(PREFIX.length())))
                .orElse(0);
        int next = Math.max(row.getLastValue(), highestUsed) + 1;
        row.advanceTo(next);
        return String.format(PREFIX + "%06d", next);
    }
}
