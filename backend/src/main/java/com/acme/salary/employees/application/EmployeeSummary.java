package com.acme.salary.employees.application;

import com.acme.salary.employees.api.EmploymentStatus;
import com.acme.salary.employees.api.EmploymentType;
import com.acme.salary.shared.domain.Country;
import com.acme.salary.shared.domain.Money;
import java.time.LocalDate;
import java.util.UUID;

/** One row of the directory: who the person is and what they are paid now (null when no current salary). */
public record EmployeeSummary(
        UUID id,
        String employeeId,
        String firstName,
        String lastName,
        String workEmail,
        String jobTitle,
        String department,
        Country country,
        String city,
        EmploymentType employmentType,
        LocalDate hireDate,
        EmploymentStatus status,
        Money salary,
        Money salaryUsd,
        long version) {}
