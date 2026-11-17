package com.acme.salary.employees.web;

import com.acme.salary.employees.application.DirectoryPage;
import com.acme.salary.employees.application.EmployeeDirectoryService;
import com.acme.salary.employees.domain.DirectoryQuery;
import com.acme.salary.shared.web.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees")
class EmployeeDirectoryController {

    private final EmployeeDirectoryService directory;

    EmployeeDirectoryController(EmployeeDirectoryService directory) {
        this.directory = directory;
    }

    @GetMapping
    PageResponse<EmployeeResponse> list(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String jobTitle,
            @RequestParam(required = false) String employmentType,
            @RequestParam(required = false) String status) {
        DirectoryQuery query = DirectoryQuery.of(page, size, sort, q, country, department, jobTitle, employmentType, status);
        DirectoryPage result = directory.search(query);
        return new PageResponse<>(
                result.items().stream().map(EmployeeResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }

    @GetMapping("/filters")
    FilterOptionsResponse filters() {
        return FilterOptionsResponse.from(directory.filterOptions());
    }
}
