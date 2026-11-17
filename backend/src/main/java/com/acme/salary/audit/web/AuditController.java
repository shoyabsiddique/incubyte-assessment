package com.acme.salary.audit.web;

import com.acme.salary.audit.application.AuditPage;
import com.acme.salary.audit.application.AuditQueryService;
import com.acme.salary.audit.domain.AuditQuery;
import com.acme.salary.shared.web.PageResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only: the trail can be looked at, never changed. */
@RestController
class AuditController {

    private final AuditQueryService audit;

    AuditController(AuditQueryService audit) {
        this.audit = audit;
    }

    @GetMapping("/api/v1/audit")
    PageResponse<AuditEntryResponse> organisation(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return respond(audit.search(AuditQuery.of(page, size, userId, entityType, from, to)));
    }

    @GetMapping("/api/v1/employees/{id}/audit")
    PageResponse<AuditEntryResponse> employee(
            @PathVariable String id,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        AuditQuery query = AuditQuery.of(page, size, null, null, null, null);
        UUID employeeId;
        try {
            employeeId = UUID.fromString(id);
        } catch (IllegalArgumentException notAnId) {
            return new PageResponse<>(List.of(), query.page(), query.size(), 0, 0);
        }
        return respond(audit.search(query.forEmployee(employeeId)));
    }

    private static PageResponse<AuditEntryResponse> respond(AuditPage result) {
        int pages = (int) Math.ceil((double) result.totalElements() / result.size());
        return new PageResponse<>(
                result.items().stream().map(AuditEntryResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                pages);
    }
}
