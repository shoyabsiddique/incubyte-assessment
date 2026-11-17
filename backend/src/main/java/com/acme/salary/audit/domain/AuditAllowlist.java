package com.acme.salary.audit.domain;

import com.acme.salary.audit.api.AuditEntityType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** The only fields each entity type may put in an audit entry; nothing else is ever logged. */
public final class AuditAllowlist {

    private static final Map<AuditEntityType, Set<String>> ALLOWED = Map.of(
            AuditEntityType.EMPLOYEE,
            Set.of(
                    "employeeId",
                    "firstName",
                    "lastName",
                    "workEmail",
                    "jobTitle",
                    "department",
                    "country",
                    "city",
                    "employmentType",
                    "hireDate",
                    "status",
                    "salary",
                    "salaryEffectiveStart"),
            AuditEntityType.COMPENSATION_RECORD,
            Set.of("amount", "effectiveStart", "effectiveEnd"),
            AuditEntityType.EXCHANGE_RATE,
            Set.of("currency", "rate"));

    private AuditAllowlist() {}

    public static Set<String> fieldsOf(AuditEntityType type) {
        return ALLOWED.get(type);
    }

    /** The allowed fields of {@code payload}, or null when there is no payload. */
    public static Map<String, Object> filter(AuditEntityType type, Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Set<String> allowed = ALLOWED.get(type);
        Map<String, Object> kept = new LinkedHashMap<>();
        payload.forEach((field, value) -> {
            if (allowed.contains(field)) {
                kept.put(field, value);
            }
        });
        return kept;
    }
}
