package com.acme.salary.employees.web;

import com.acme.salary.employees.application.EmployeeSummary;
import com.acme.salary.shared.web.MoneyResponse;

record EmployeeResponse(
        String id,
        String employeeId,
        String firstName,
        String lastName,
        String workEmail,
        String jobTitle,
        String department,
        String country,
        String countryName,
        String city,
        String employmentType,
        String status,
        String hireDate,
        MoneyResponse salary,
        MoneyResponse salaryUsd,
        long version) {

    static EmployeeResponse from(EmployeeSummary e) {
        return new EmployeeResponse(
                e.id().toString(), e.employeeId(), e.firstName(), e.lastName(), e.workEmail(), e.jobTitle(),
                e.department(), e.country().name(), e.country().displayName(), e.city(), e.employmentType().name(),
                e.status().name(), e.hireDate().toString(), MoneyResponse.from(e.salary()), MoneyResponse.from(e.salaryUsd()), e.version());
    }
}
