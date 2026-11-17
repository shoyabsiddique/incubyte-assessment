package com.acme.salary.audit.application;

import com.acme.salary.audit.domain.AuditQuery;
import com.acme.salary.audit.persistence.AuditEntryEntity;
import com.acme.salary.audit.persistence.AuditEntryStore;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    private static final TypeReference<Map<String, Object>> FIELDS = new TypeReference<>() {};

    private final AuditEntryStore store;
    private final JsonMapper json;

    AuditQueryService(AuditEntryStore store, JsonMapper json) {
        this.store = store;
        this.json = json;
    }

    public AuditPage search(AuditQuery query) {
        var items = store.page(query).stream().map(this::view).toList();
        return new AuditPage(items, query.page(), query.size(), store.count(query));
    }

    private AuditEntryView view(AuditEntryEntity entry) {
        return new AuditEntryView(
                entry.getId(),
                entry.getUserId(),
                entry.getUserEmail(),
                entry.getEntityType(),
                entry.getEntityId(),
                entry.getSubjectEmployeeId(),
                entry.getOperation(),
                fields(entry.getBeforeJson()),
                fields(entry.getAfterJson()),
                entry.getCreatedAt());
    }

    private Map<String, Object> fields(String stored) {
        return stored == null ? null : json.readValue(stored, FIELDS);
    }
}
