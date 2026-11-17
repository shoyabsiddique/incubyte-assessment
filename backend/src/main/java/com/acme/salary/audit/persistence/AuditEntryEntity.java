package com.acme.salary.audit.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_entry")
public class AuditEntryEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", length = 36, nullable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "user_id", length = 36, nullable = false)
    private UUID userId;

    @Column(name = "user_email", length = 254, nullable = false)
    private String userEmail;

    @Column(name = "entity_type", length = 30, nullable = false)
    private String entityType;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "entity_id", length = 36, nullable = false)
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "subject_employee_id", length = 36)
    private UUID subjectEmployeeId;

    @Column(name = "operation", length = 10, nullable = false)
    private String operation;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "before_json", columnDefinition = "longtext")
    private String beforeJson;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "after_json", columnDefinition = "longtext")
    private String afterJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEntryEntity() {}

    public AuditEntryEntity(
            UUID id,
            UUID userId,
            String userEmail,
            String entityType,
            UUID entityId,
            UUID subjectEmployeeId,
            String operation,
            String beforeJson,
            String afterJson,
            Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.userEmail = userEmail;
        this.entityType = entityType;
        this.entityId = entityId;
        this.subjectEmployeeId = subjectEmployeeId;
        this.operation = operation;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public UUID getSubjectEmployeeId() {
        return subjectEmployeeId;
    }

    public String getOperation() {
        return operation;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
