package com.acme.salary.compensation.web;

import com.acme.salary.compensation.application.SalaryHistoryService;
import com.acme.salary.shared.domain.DomainException;
import com.acme.salary.shared.domain.ErrorKind;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees/{id}/salary-history")
class SalaryHistoryController {

    private final SalaryHistoryService history;

    SalaryHistoryController(SalaryHistoryService history) {
        this.history = history;
    }

    @GetMapping
    List<SalaryRecordResponse> list(@PathVariable String id) {
        UUID employeeId;
        try {
            employeeId = UUID.fromString(id);
        } catch (IllegalArgumentException notAnId) {
            throw new DomainException(ErrorKind.NOT_FOUND, "SALARY_HISTORY_NOT_FOUND", "No salary history for this employee");
        }
        return history.historyOf(employeeId).stream().map(SalaryRecordResponse::from).toList();
    }
}
