package com.acme.salary.employees;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.auth.persistence.AppUserRepository;
import com.acme.salary.support.ApiClient;
import com.acme.salary.support.ApiClient.Reply;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class EmployeeCreateIT extends AbstractMariaDbIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    private ApiClient api;
    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void signIn() throws Exception {
        api = new ApiClient(port, users, encoder);
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM audit_entry WHERE user_id = ?", api.userId().toString());
        jdbc.update("DELETE FROM compensation_record WHERE employee_id IN (SELECT id FROM employee WHERE work_email LIKE ?)", tag + "%");
        jdbc.update("DELETE FROM employee WHERE work_email LIKE ?", tag + "%");
        jdbc.update("DELETE FROM app_user WHERE id = ?", api.userId().toString());
    }

    private String body(String email) throws Exception {
        return JSON.writeValueAsString(Map.ofEntries(
                Map.entry("firstName", "Ada"),
                Map.entry("lastName", "Lovelace"),
                Map.entry("workEmail", email),
                Map.entry("jobTitle", "  Software   Engineer "),
                Map.entry("department", "Engineering"),
                Map.entry("country", "AUSTRALIA"),
                Map.entry("city", "Sydney"),
                Map.entry("employmentType", "FULL_TIME"),
                Map.entry("hireDate", "2024-03-01"),
                Map.entry("status", "ACTIVE"),
                Map.entry("salaryAmount", "145700.50"),
                Map.entry("salaryCurrency", "AUD"),
                Map.entry("salaryEffectiveStart", "2024-03-01")));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void createsTheEmployeeTheOpenSalaryAndExactlyOneAuditEntry() throws Exception {
        Reply created = api.post("/api/v1/employees", body(tag + ".ada@acme.example"));

        assertThat(created.status()).isEqualTo(201);
        JsonNode employee = created.body();
        assertThat(employee.get("employeeId").asString()).matches("EMP-\\d{6}");
        assertThat(employee.get("jobTitle").asString()).isEqualTo("Software Engineer");
        assertThat(employee.get("salary").get("amount").asString()).isEqualTo("145700.50");
        assertThat(employee.get("salary").get("currency").asString()).isEqualTo("AUD");
        assertThat(employee.get("version").asLong()).isZero();
        UUID id = UUID.fromString(employee.get("id").asString());

        assertThat(count("SELECT COUNT(*) FROM compensation_record WHERE employee_id = ? AND effective_end IS NULL", id.toString()))
                .isEqualTo(1);
        List<Map<String, Object>> entries = jdbc.queryForList("SELECT * FROM audit_entry WHERE user_id = ?", api.userId().toString());
        assertThat(entries).hasSize(1);
        Map<String, Object> entry = entries.get(0);
        assertThat(entry.get("entity_type")).isEqualTo("EMPLOYEE");
        assertThat(entry.get("operation")).isEqualTo("CREATE");
        assertThat(entry.get("subject_employee_id")).isEqualTo(id.toString());
        assertThat(entry.get("before_json")).isNull();
        JsonNode after = JSON.readTree(entry.get("after_json").toString());
        assertThat(after.get("workEmail").asString()).isEqualTo(tag + ".ada@acme.example");
        assertThat(after.get("salary").get("amount").asString()).isEqualTo("145700.50");
        assertThat(after.get("salary").get("currency").asString()).isEqualTo("AUD");
    }

    @Test
    void givesEachNewEmployeeTheNextEmployeeId() throws Exception {
        String first = api.post("/api/v1/employees", body(tag + ".one@acme.example")).body().get("employeeId").asString();
        String second = api.post("/api/v1/employees", body(tag + ".two@acme.example")).body().get("employeeId").asString();

        assertThat(Integer.parseInt(second.substring(4))).isEqualTo(Integer.parseInt(first.substring(4)) + 1);
    }

    @Test
    void aDuplicateEmailIgnoringCaseIsAConflictAndStoresNothing() throws Exception {
        api.post("/api/v1/employees", body(tag + ".dup@acme.example"));
        int employees = count("SELECT COUNT(*) FROM employee");
        int entries = count("SELECT COUNT(*) FROM audit_entry WHERE user_id = ?", api.userId().toString());

        Reply again = api.post("/api/v1/employees", body(tag + ".DUP@ACME.example"));

        assertThat(again.status()).isEqualTo(409);
        assertThat(again.body().get("code").asString()).isEqualTo("DUPLICATE_EMPLOYEE_EMAIL");
        assertThat(count("SELECT COUNT(*) FROM employee")).isEqualTo(employees);
        assertThat(count("SELECT COUNT(*) FROM audit_entry WHERE user_id = ?", api.userId().toString())).isEqualTo(entries);
    }

    @Test
    void invalidInputListsEachFieldErrorAndStoresNothing() throws Exception {
        int employees = count("SELECT COUNT(*) FROM employee");

        Reply bad = api.post("/api/v1/employees", "{\"firstName\":\"\",\"workEmail\":\"x\",\"salaryAmount\":\"-1\"}");

        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.body().get("code").asString()).isEqualTo("VALIDATION_FAILED");
        List<String> fields = bad.body().get("errors").valueStream().map(e -> e.get("field").asString()).toList();
        assertThat(fields).contains("firstName", "lastName", "workEmail", "salaryAmount", "salaryCurrency");
        assertThat(count("SELECT COUNT(*) FROM employee")).isEqualTo(employees);
    }

    @Test
    void aSalaryCurrencyThatDoesNotMatchTheCountryIsRejected() throws Exception {
        String wrong = body(tag + ".fx@acme.example").replace("\"AUD\"", "\"USD\"");

        Reply reply = api.post("/api/v1/employees", wrong);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.body().get("errors").get(0).get("field").asString()).isEqualTo("salaryCurrency");
    }

    @Test
    void requiresASignedInUser() throws Exception {
        assertThat(api.withoutToken("POST", "/api/v1/employees", body(tag + ".anon@acme.example")).status()).isEqualTo(401);
    }

    @Test
    void theNewEmployeeAppearsInTheDirectory() throws Exception {
        Reply created = api.post("/api/v1/employees", body(tag + ".find@acme.example"));

        Reply found = api.get("/api/v1/employees?q=" + tag + ".find");

        assertThat(found.body().get("totalElements").asInt()).isEqualTo(1);
        assertThat(found.body().get("items").get(0).get("id").asString()).isEqualTo(created.body().get("id").asString());
    }
}
