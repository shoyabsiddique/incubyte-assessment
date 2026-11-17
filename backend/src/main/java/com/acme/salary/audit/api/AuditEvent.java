package com.acme.salary.audit.api;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One business operation, described for the audit trail. {@code before} and {@code after} carry
 * the changed fields only; fields an entity type does not allow are dropped when it is recorded.
 * Money is passed as an object with {@code amount} and {@code currency} strings.
 */
public record AuditEvent(
        UUID userId,
        String userEmail,
        AuditEntityType entityType,
        UUID entityId,
        UUID subjectEmployeeId,
        AuditOperation operation,
        Map<String, Object> before,
        Map<String, Object> after) {

    public AuditEvent {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(userEmail, "userEmail");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(operation, "operation");
    }
}
