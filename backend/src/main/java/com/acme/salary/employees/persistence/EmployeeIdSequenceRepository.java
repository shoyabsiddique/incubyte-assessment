package com.acme.salary.employees.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface EmployeeIdSequenceRepository extends JpaRepository<EmployeeIdSequenceEntity, Byte> {

    /** The one sequence row, locked until the caller's transaction ends so concurrent additions queue up. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EmployeeIdSequenceEntity s where s.id = 1")
    EmployeeIdSequenceEntity lockTheSequence();
}
