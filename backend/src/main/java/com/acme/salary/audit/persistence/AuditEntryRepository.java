package com.acme.salary.audit.persistence;

import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Insert-only on purpose: this interface exposes no update and no delete. */
public interface AuditEntryRepository extends Repository<AuditEntryEntity, UUID> {

    AuditEntryEntity save(AuditEntryEntity entry);
}
