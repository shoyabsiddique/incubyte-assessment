package com.acme.salary.employees.web;

import com.acme.salary.employees.domain.NewEmployee;
import com.acme.salary.employees.domain.ProfileInput;
import com.acme.salary.employees.domain.SalaryInput;

record CreateEmployeeRequest(
        String firstName,
        String lastName,
        String workEmail,
        String jobTitle,
        String department,
        String country,
        String city,
        String employmentType,
        String hireDate,
        String status,
        String salaryAmount,
        String salaryCurrency,
        String salaryEffectiveStart) {

    NewEmployee parse() {
        return NewEmployee.parse(
                new ProfileInput(
                        firstName, lastName, workEmail, jobTitle, department, country, city, employmentType, hireDate, status),
                new SalaryInput(salaryAmount, salaryCurrency, salaryEffectiveStart));
    }
}
