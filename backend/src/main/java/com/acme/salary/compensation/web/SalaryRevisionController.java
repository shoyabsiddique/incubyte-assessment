package com.acme.salary.compensation.web;

import com.acme.salary.compensation.application.SalaryHistoryService;
import com.acme.salary.compensation.application.SalaryRevisionService;
import com.acme.salary.shared.domain.DomainException;
import com.acme.salary.shared.domain.ErrorKind;
import com.acme.salary.shared.web.ActorResolver;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees/{id}/salary-revisions")
class SalaryRevisionController {

    private final SalaryRevisionService revisions;
    private final SalaryHistoryService history;

    SalaryRevisionController(SalaryRevisionService revisions, SalaryHistoryService history) {
        this.revisions = revisions;
        this.history = history;
    }

    @PostMapping
    ResponseEntity<SalaryRecordResponse> revise(
            @AuthenticationPrincipal Jwt token, @PathVariable String id, @RequestBody RevisionRequest request) {
        UUID employeeId;
        try {
            employeeId = UUID.fromString(id);
        } catch (IllegalArgumentException notAnId) {
            throw new DomainException(ErrorKind.NOT_FOUND, "EMPLOYEE_NOT_FOUND", "There is no employee with this id");
        }
        UUID created = revisions.revise(ActorResolver.from(token), employeeId, request.amount(), request.effectiveStart());
        SalaryRecordResponse body = history.historyOf(employeeId).stream()
                .filter(entry -> entry.id().equals(created))
                .findFirst()
                .map(SalaryRecordResponse::from)
                .orElseThrow();
        return ResponseEntity.created(URI.create("/api/v1/employees/" + employeeId + "/salary-history")).body(body);
    }

    record RevisionRequest(String amount, String effectiveStart) {}
}
