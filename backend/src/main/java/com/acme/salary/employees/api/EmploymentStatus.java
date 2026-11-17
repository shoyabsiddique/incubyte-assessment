package com.acme.salary.employees.api;

/** Whether someone currently works for ACME. Analytics only look at ACTIVE employees. */
public enum EmploymentStatus {
    ACTIVE,
    ON_LEAVE,
    TERMINATED
}
