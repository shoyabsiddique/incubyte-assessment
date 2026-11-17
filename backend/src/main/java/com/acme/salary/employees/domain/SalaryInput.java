package com.acme.salary.employees.domain;

/** A starting salary as the client sent it. */
public record SalaryInput(String amount, String currency, String effectiveStart) {}
