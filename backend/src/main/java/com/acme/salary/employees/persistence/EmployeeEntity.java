package com.acme.salary.employees.persistence;

import com.acme.salary.employees.api.EmploymentStatus;
import com.acme.salary.employees.api.EmploymentType;
import com.acme.salary.shared.domain.Country;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "employee")
public class EmployeeEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", length = 36, nullable = false)
    private UUID id;

    @Column(name = "employee_id", length = 20, nullable = false)
    private String employeeId;

    @Column(name = "first_name", length = 100, nullable = false)
    private String firstName;

    @Column(name = "last_name", length = 100, nullable = false)
    private String lastName;

    @Column(name = "work_email", length = 254, nullable = false)
    private String workEmail;

    @Column(name = "job_title", length = 100, nullable = false)
    private String jobTitle;

    @Column(name = "department", length = 100, nullable = false)
    private String department;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "country", length = 30, nullable = false)
    private Country country;

    @Column(name = "city", length = 100, nullable = false)
    private String city;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "employment_type", length = 20, nullable = false)
    private EmploymentType employmentType;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 20, nullable = false)
    private EmploymentStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected EmployeeEntity() {}

    public UUID getId() {
        return id;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getWorkEmail() {
        return workEmail;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public String getDepartment() {
        return department;
    }

    public Country getCountry() {
        return country;
    }

    public String getCity() {
        return city;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public EmploymentStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }
}
