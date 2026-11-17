package com.acme.salary.audit.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** One audit entry as it is shown: who, when, what, and the changed fields before and after. */
public record AuditEntryView(
        UUID id,
        UUID userId,
        String userEmail,
        String entityType,
        UUID entityId,
        UUID subjectEmployeeId,
        String operation,
        Map<String, Object> before,
        Map<String, Object> after,
        Instant createdAt) {}
