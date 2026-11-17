package com.acme.salary.employees.application;

import com.acme.salary.employees.api.EmploymentStatus;
import com.acme.salary.employees.api.EmploymentType;
import com.acme.salary.shared.domain.Country;
import java.util.List;

/** The values the directory's filter dropdowns offer. */
public record FilterOptions(
        List<Country> countries,
        List<String> departments,
        List<String> jobTitles,
        List<EmploymentType> employmentTypes,
        List<EmploymentStatus> statuses) {}
