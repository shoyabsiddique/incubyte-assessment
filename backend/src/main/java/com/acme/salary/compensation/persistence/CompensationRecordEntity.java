package com.acme.salary.compensation.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One salary period. The open record (no end date) is the current salary. */
@Entity
@Table(name = "compensation_record")
public class CompensationRecordEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", length = 36, nullable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "employee_id", length = 36, nullable = false)
    private UUID employeeId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "effective_start", nullable = false)
    private LocalDate effectiveStart;

    @Column(name = "effective_end")
    private LocalDate effectiveEnd;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected CompensationRecordEntity() {}

    /** A new open record: the salary in force from {@code effectiveStart} until it is revised. */
    public static CompensationRecordEntity open(
            UUID id, UUID employeeId, BigDecimal amount, String currency, LocalDate effectiveStart) {
        CompensationRecordEntity record = new CompensationRecordEntity();
        record.id = id;
        record.employeeId = employeeId;
        record.amount = amount;
        record.currency = currency;
        record.effectiveStart = effectiveStart;
        return record;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getEffectiveStart() {
        return effectiveStart;
    }

    public LocalDate getEffectiveEnd() {
        return effectiveEnd;
    }

    public long getVersion() {
        return version;
    }
}
