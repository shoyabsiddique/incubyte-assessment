package com.acme.salary.employees.domain;

/** The profile fields exactly as the client sent them; nothing is trusted until parsed. */
public record ProfileInput(
        String firstName,
        String lastName,
        String workEmail,
        String jobTitle,
        String department,
        String country,
        String city,
        String employmentType,
        String hireDate,
        String status) {}
