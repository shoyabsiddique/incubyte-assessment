package com.acme.salary.analytics.domain;

import com.acme.salary.shared.domain.Country;
import java.math.BigDecimal;
import java.time.LocalDate;

/** One active employee as the dashboard sees them: the fields that can be grouped on, and their pay in USD. */
public record DashboardRow(
        Country country,
        String department,
        String jobTitle,
        String city,
        String employmentType,
        LocalDate hireDate,
        BigDecimal salaryUsd) {}
