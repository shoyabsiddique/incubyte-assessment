package com.acme.salary.compensation.persistence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompensationRecordRepository extends JpaRepository<CompensationRecordEntity, UUID> {

    /** Projection query: thousands of rows are read cheaply, with no entities kept in memory. */
    @Query("select new com.acme.salary.compensation.persistence.CurrentSalaryRow(r.employeeId, r.amount, r.currency) "
            + "from CompensationRecordEntity r where r.employeeId in :ids "
            + "and r.effectiveStart <= :today and (r.effectiveEnd is null or r.effectiveEnd >= :today) "
            + "order by r.effectiveStart desc")
    List<CurrentSalaryRow> findCurrent(@Param("ids") Collection<UUID> ids, @Param("today") LocalDate today);

    /** Everyone's current pay in one query, cheaper than many large IN lists when most people are wanted. */
    @Query("select new com.acme.salary.compensation.persistence.CurrentSalaryRow(r.employeeId, r.amount, r.currency) "
            + "from CompensationRecordEntity r "
            + "where r.effectiveStart <= :today and (r.effectiveEnd is null or r.effectiveEnd >= :today) "
            + "order by r.effectiveStart desc")
    List<CurrentSalaryRow> findAllCurrent(@Param("today") LocalDate today);
}
