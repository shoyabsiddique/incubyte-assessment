package com.acme.salary.employees.web;

import com.acme.salary.employees.domain.ProfileInput;

record UpdateEmployeeRequest(
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
        Long version) {

    ProfileInput profile() {
        return new ProfileInput(
                firstName, lastName, workEmail, jobTitle, department, country, city, employmentType, hireDate, status);
    }
}
