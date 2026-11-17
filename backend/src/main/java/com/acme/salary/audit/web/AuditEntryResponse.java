package com.acme.salary.audit.web;

import com.acme.salary.audit.application.AuditEntryView;
import java.util.Map;

record AuditEntryResponse(
        String id,
        String userId,
        String userEmail,
        String entityType,
        String entityId,
        String subjectEmployeeId,
        String operation,
        Map<String, Object> before,
        Map<String, Object> after,
        String createdAt) {

    static AuditEntryResponse from(AuditEntryView entry) {
        return new AuditEntryResponse(
                entry.id().toString(),
                entry.userId().toString(),
                entry.userEmail(),
                entry.entityType(),
                entry.entityId().toString(),
                entry.subjectEmployeeId() == null ? null : entry.subjectEmployeeId().toString(),
                entry.operation(),
                entry.before(),
                entry.after(),
                entry.createdAt().toString());
    }
}
