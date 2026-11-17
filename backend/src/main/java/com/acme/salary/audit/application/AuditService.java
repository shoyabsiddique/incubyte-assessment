package com.acme.salary.audit.application;

import com.acme.salary.audit.api.AuditEvent;
import com.acme.salary.audit.api.AuditTrail;
import com.acme.salary.audit.domain.AuditAllowlist;
import com.acme.salary.audit.persistence.AuditEntryEntity;
import com.acme.salary.audit.persistence.AuditEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
class AuditService implements AuditTrail {

    private final AuditEntryRepository entries;
    private final Clock clock;
    private final JsonMapper json;

    AuditService(AuditEntryRepository entries, Clock clock, JsonMapper json) {
        this.entries = entries;
        this.clock = clock;
        this.json = json;
    }

    /** MANDATORY: joins the caller's transaction and refuses to run without one. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEvent event) {
        entries.save(new AuditEntryEntity(
                UUID.randomUUID(),
                event.userId(),
                event.userEmail(),
                event.entityType().name(),
                event.entityId(),
                event.subjectEmployeeId(),
                event.operation().name(),
                serialize(AuditAllowlist.filter(event.entityType(), event.before())),
                serialize(AuditAllowlist.filter(event.entityType(), event.after())),
                Instant.now(clock)));
    }

    private String serialize(Map<String, Object> payload) {
        return payload == null ? null : json.writeValueAsString(payload);
    }
}
