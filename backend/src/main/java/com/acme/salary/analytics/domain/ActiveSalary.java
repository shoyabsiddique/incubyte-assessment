package com.acme.salary.analytics.domain;

import com.acme.salary.shared.domain.Country;
import com.acme.salary.shared.domain.Money;

/** One active employee and the salary they are paid today, as the analytics view reports it. */
public record ActiveSalary(
        String id,
        String employeeId,
        String name,
        Country country,
        String department,
        String jobTitle,
        Money salary) {}
