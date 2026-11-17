package com.acme.salary.employees.application;

import com.acme.salary.employees.domain.EmployeeDraft;
import com.acme.salary.employees.persistence.EmployeeEntity;
import java.util.LinkedHashMap;
import java.util.Map;

/** The employee fields that go into the audit trail, in one place so create and edit agree. */
final class EmployeeAuditFields {

    private EmployeeAuditFields() {}

    static Map<String, Object> of(EmployeeEntity employee) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("firstName", employee.getFirstName());
        fields.put("lastName", employee.getLastName());
        fields.put("workEmail", employee.getWorkEmail());
        fields.put("jobTitle", employee.getJobTitle());
        fields.put("department", employee.getDepartment());
        fields.put("country", employee.getCountry().name());
        fields.put("city", employee.getCity());
        fields.put("employmentType", employee.getEmploymentType().name());
        fields.put("hireDate", employee.getHireDate().toString());
        fields.put("status", employee.getStatus().name());
        return fields;
    }

    static Map<String, Object> of(EmployeeEntity employee, Map<String, String> salary, String salaryStart) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("employeeId", employee.getEmployeeId());
        fields.putAll(of(employee));
        fields.put("salary", salary);
        fields.put("salaryEffectiveStart", salaryStart);
        return fields;
    }
}
