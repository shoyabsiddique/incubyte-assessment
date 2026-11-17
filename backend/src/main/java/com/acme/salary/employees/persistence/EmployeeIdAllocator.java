package com.acme.salary.employees.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allocates the next Employee ID. The sequence row stays locked until the caller's transaction ends, so
 * concurrent additions queue up instead of colliding. IDs already present (the seeded workforce) are respected.
 */
@Component
public class EmployeeIdAllocator {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next() {
        long last = ((Number) entityManager
                        .createNativeQuery("SELECT last_value FROM employee_id_sequence WHERE id = 1 FOR UPDATE")
                        .getSingleResult())
                .longValue();
        long highestUsed = ((Number) entityManager
                        .createNativeQuery("SELECT COALESCE(MAX(CAST(SUBSTRING(employee_id, 5) AS UNSIGNED)), 0) FROM employee")
                        .getSingleResult())
                .longValue();
        long next = Math.max(last, highestUsed) + 1;
        entityManager
                .createNativeQuery("UPDATE employee_id_sequence SET last_value = :next WHERE id = 1")
                .setParameter("next", next)
                .executeUpdate();
        return String.format("EMP-%06d", next);
    }
}
