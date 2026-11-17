package com.acme.salary.seed.domain;

import com.acme.salary.employees.api.EmploymentStatus;
import com.acme.salary.employees.api.EmploymentType;
import com.acme.salary.shared.domain.Country;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A generated employee with their whole salary history, ready to be written. */
public record SeedEmployee(
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
        List<SeedCompensation> compensation) {}
