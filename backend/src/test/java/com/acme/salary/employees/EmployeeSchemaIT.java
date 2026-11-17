package com.acme.salary.employees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Every rule the employee and compensation tables enforce, checked against the real database. */
class EmployeeSchemaIT extends AbstractMariaDbIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM compensation_record");
        jdbc.update("DELETE FROM employee");
    }

    private String insertEmployee(String employeeId, String email, String status) {
        return insertEmployee(employeeId, email, status, "INDIA", "FULL_TIME");
    }

    private String insertEmployee(String employeeId, String email, String status, String country, String type) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO employee (id, employee_id, first_name, last_name, work_email, job_title, department, "
                        + "country, city, employment_type, hire_date, status) "
                        + "VALUES (?, ?, 'Asha', 'Rao', ?, 'Software Engineer', 'Engineering', ?, 'Pune', ?, '2020-01-15', ?)",
                id, employeeId, email, country, type, status);
        return id;
    }

    private void insertRecord(String employeeId, String amount, String currency, String start, String end) {
        jdbc.update(
                "INSERT INTO compensation_record (id, employee_id, amount, currency, effective_start, effective_end) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), employeeId, amount, currency, start, end);
    }

    @Autowired
    private org.springframework.core.env.Environment environment;

    @Test
    void datesAreHandledInUtcByBothTheJvmAndHibernate() {
        assertThat(java.util.TimeZone.getDefault().getID()).isEqualTo("UTC");
        assertThat(environment.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone")).isEqualTo("UTC");
    }

    @Test
    void acceptsEveryCountryTheApplicationKnowsAbout() {
        int n = 0;
        for (com.acme.salary.shared.domain.Country country : com.acme.salary.shared.domain.Country.values()) {
            insertEmployee("EMP-91000" + n, "country" + n + "@acme.example", "ACTIVE", country.name(), "FULL_TIME");
            n++;
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM employee", Integer.class))
                .isEqualTo(com.acme.salary.shared.domain.Country.values().length);
    }

    @Test
    void rejectsAnUnknownCountryAndAnUnknownEmploymentType() {
        assertThatThrownBy(() -> insertEmployee("EMP-900011", "c@acme.example", "ACTIVE", "FRANCE", "FULL_TIME"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertEmployee("EMP-900012", "t@acme.example", "ACTIVE", "INDIA", "INTERN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsASecondOpenRecordForTheSameEmployee() {
        String employee = insertEmployee("EMP-900013", "open2@acme.example", "ACTIVE");
        insertRecord(employee, "100.00", "INR", "2020-01-15", null);

        assertThatThrownBy(() -> insertRecord(employee, "110.00", "INR", "2021-01-15", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsAnyNumberOfClosedRecordsPlusOneOpenRecord() {
        String employee = insertEmployee("EMP-900014", "chain@acme.example", "ACTIVE");
        insertRecord(employee, "100.00", "INR", "2020-01-15", "2021-01-14");
        insertRecord(employee, "110.00", "INR", "2021-01-15", "2022-01-14");
        insertRecord(employee, "120.00", "INR", "2022-01-15", null);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM compensation_record WHERE employee_id = ?", Integer.class, employee))
                .isEqualTo(3);
    }

    @Test
    void storesAValidEmployeeWithItsVersionStartingAtZero() {
        String id = insertEmployee("EMP-900001", "asha.rao@acme.example", "ACTIVE");

        assertThat(jdbc.queryForObject("SELECT version FROM employee WHERE id = ?", Long.class, id)).isZero();
    }

    @Test
    void rejectsADuplicateEmployeeId() {
        insertEmployee("EMP-900002", "one@acme.example", "ACTIVE");

        assertThatThrownBy(() -> insertEmployee("EMP-900002", "two@acme.example", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsADuplicateWorkEmailIgnoringCase() {
        insertEmployee("EMP-900003", "same@acme.example", "ACTIVE");

        assertThatThrownBy(() -> insertEmployee("EMP-900004", "SAME@ACME.EXAMPLE", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAnUnknownStatus() {
        assertThatThrownBy(() -> insertEmployee("EMP-900005", "x@acme.example", "RETIRED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsARecordWithAnOpenEnd() {
        String employee = insertEmployee("EMP-900006", "open@acme.example", "ACTIVE");

        insertRecord(employee, "1500000.00", "INR", "2020-01-15", null);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM compensation_record", Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsANonPositiveAmount() {
        String employee = insertEmployee("EMP-900007", "neg@acme.example", "ACTIVE");

        assertThatThrownBy(() -> insertRecord(employee, "0", "INR", "2020-01-15", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertRecord(employee, "-5", "INR", "2020-01-15", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAnEndDateBeforeTheStartDate() {
        String employee = insertEmployee("EMP-900008", "range@acme.example", "ACTIVE");

        assertThatThrownBy(() -> insertRecord(employee, "100.00", "INR", "2021-06-01", "2021-05-31"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsTwoRecordsStartingOnTheSameDateForOneEmployee() {
        String employee = insertEmployee("EMP-900009", "dup@acme.example", "ACTIVE");
        insertRecord(employee, "100.00", "INR", "2020-01-15", "2021-01-14");

        assertThatThrownBy(() -> insertRecord(employee, "110.00", "INR", "2020-01-15", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsACurrencyThatHasNoExchangeRate() {
        String employee = insertEmployee("EMP-900010", "fx@acme.example", "ACTIVE");

        assertThatThrownBy(() -> insertRecord(employee, "100.00", "XYZ", "2020-01-15", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsARecordForAnEmployeeThatDoesNotExist() {
        assertThatThrownBy(() -> insertRecord(UUID.randomUUID().toString(), "100.00", "INR", "2020-01-15", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
