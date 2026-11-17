package com.acme.salary.employees;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.auth.persistence.AppUserRepository;
import com.acme.salary.support.ApiClient;
import com.acme.salary.support.ApiClient.Reply;
import java.util.LinkedHashMap;
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

class EmployeeEditIT extends AbstractMariaDbIntegrationTest {

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
    private String id;
    private long version;

    private Map<String, Object> profile(String email) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("firstName", "Ada");
        fields.put("lastName", "Lovelace");
        fields.put("workEmail", email);
        fields.put("jobTitle", "Software Engineer");
        fields.put("department", "Engineering");
        fields.put("country", "AUSTRALIA");
        fields.put("city", "Sydney");
        fields.put("employmentType", "FULL_TIME");
        fields.put("hireDate", "2024-03-01");
        fields.put("status", "ACTIVE");
        return fields;
    }

    @BeforeEach
    void createAnEmployee() throws Exception {
        api = new ApiClient(port, users, encoder);
        Map<String, Object> create = profile(tag + ".ada@acme.example");
        create.put("salaryAmount", "100000");
        create.put("salaryCurrency", "AUD");
        create.put("salaryEffectiveStart", "2024-03-01");
        JsonNode created = api.post("/api/v1/employees", JSON.writeValueAsString(create)).body();
        id = created.get("id").asString();
        version = created.get("version").asLong();
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM audit_entry WHERE user_id = ?", api.userId().toString());
        jdbc.update("DELETE FROM compensation_record WHERE employee_id IN (SELECT id FROM employee WHERE work_email LIKE ?)", tag + "%");
        jdbc.update("DELETE FROM employee WHERE work_email LIKE ?", tag + "%");
        jdbc.update("DELETE FROM app_user WHERE id = ?", api.userId().toString());
    }

    private Reply update(Map<String, Object> changes, Long expectedVersion) throws Exception {
        Map<String, Object> body = profile(tag + ".ada@acme.example");
        body.putAll(changes);
        body.put("version", expectedVersion);
        return api.put("/api/v1/employees/" + id, JSON.writeValueAsString(body));
    }

    private List<Map<String, Object>> updateEntries() {
        return jdbc.queryForList(
                "SELECT * FROM audit_entry WHERE user_id = ? AND operation = 'UPDATE'", api.userId().toString());
    }

    @Test
    void updatesTheProfileAndAuditsOnlyTheChangedFields() throws Exception {
        Reply reply = update(Map.of("jobTitle", "  Senior   Engineer ", "status", "ON_LEAVE"), version);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.body().get("jobTitle").asString()).isEqualTo("Senior Engineer");
        assertThat(reply.body().get("status").asString()).isEqualTo("ON_LEAVE");
        assertThat(reply.body().get("version").asLong()).isEqualTo(version + 1);
        List<Map<String, Object>> entries = updateEntries();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).get("subject_employee_id")).isEqualTo(id);
        assertThat(entries.get(0).get("entity_type")).isEqualTo("EMPLOYEE");
        JsonNode before = JSON.readTree(entries.get(0).get("before_json").toString());
        JsonNode after = JSON.readTree(entries.get(0).get("after_json").toString());
        assertThat(before.propertyNames()).containsExactlyInAnyOrder("jobTitle", "status");
        assertThat(before.get("jobTitle").asString()).isEqualTo("Software Engineer");
        assertThat(after.get("jobTitle").asString()).isEqualTo("Senior Engineer");
        assertThat(after.get("status").asString()).isEqualTo("ON_LEAVE");
    }

    @Test
    void aChangeThatChangesNothingWritesNoAuditEntryAndKeepsTheVersion() throws Exception {
        Reply reply = update(Map.of(), version);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.body().get("version").asLong()).isEqualTo(version);
        assertThat(updateEntries()).isEmpty();
    }

    @Test
    void aStaleVersionIsAConflictAndChangesNothing() throws Exception {
        update(Map.of("city", "Melbourne"), version);

        Reply stale = update(Map.of("city", "Perth"), version);

        assertThat(stale.status()).isEqualTo(409);
        assertThat(stale.body().get("code").asString()).isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(jdbc.queryForObject("SELECT city FROM employee WHERE id = ?", String.class, id)).isEqualTo("Melbourne");
        assertThat(updateEntries()).hasSize(1);
    }

    @Test
    void anEmailAlreadyUsedByAnotherEmployeeIsAConflict() throws Exception {
        Map<String, Object> other = profile(tag + ".other@acme.example");
        other.put("salaryAmount", "90000");
        other.put("salaryCurrency", "AUD");
        other.put("salaryEffectiveStart", "2024-03-01");
        api.post("/api/v1/employees", JSON.writeValueAsString(other));

        Reply reply = update(Map.of("workEmail", tag + ".OTHER@acme.example"), version);

        assertThat(reply.status()).isEqualTo(409);
        assertThat(reply.body().get("code").asString()).isEqualTo("DUPLICATE_EMPLOYEE_EMAIL");
        assertThat(updateEntries()).isEmpty();
    }

    @Test
    void keepingYourOwnEmailIsNotAConflict() throws Exception {
        assertThat(update(Map.of("workEmail", tag + ".ADA@acme.example"), version).status()).isEqualTo(200);
    }

    @Test
    void changingTheCountryKeepsTheSalaryCurrencyAndIsAudited() throws Exception {
        Reply reply = update(Map.of("country", "INDIA", "city", "Pune"), version);

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.body().get("country").asString()).isEqualTo("INDIA");
        assertThat(reply.body().get("salary").get("currency").asString()).isEqualTo("AUD");
        assertThat(JSON.readTree(updateEntries().get(0).get("after_json").toString()).get("country").asString()).isEqualTo("INDIA");
    }

    @Test
    void invalidInputUnknownEmployeesAndMissingTokensAreRejected() throws Exception {
        Reply invalid = update(Map.of("firstName", ""), version);
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.body().get("code").asString()).isEqualTo("VALIDATION_FAILED");

        Reply noVersion = update(Map.of(), null);
        assertThat(noVersion.status()).isEqualTo(400);
        assertThat(noVersion.body().get("errors").get(0).get("field").asString()).isEqualTo("version");

        Reply missing = api.put("/api/v1/employees/" + UUID.randomUUID(), JSON.writeValueAsString(profile("x@y.zz")));
        assertThat(missing.status()).isEqualTo(404);
        assertThat(missing.body().get("code").asString()).isEqualTo("EMPLOYEE_NOT_FOUND");

        assertThat(api.withoutToken("PUT", "/api/v1/employees/" + id, "{}").status()).isEqualTo(401);
    }
}
