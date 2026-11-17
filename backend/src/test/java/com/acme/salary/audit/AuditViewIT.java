package com.acme.salary.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.AbstractMariaDbIntegrationTest;
import com.acme.salary.auth.persistence.AppUserRepository;
import com.acme.salary.support.ApiClient;
import com.acme.salary.support.ApiClient.Reply;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;

class AuditViewIT extends AbstractMariaDbIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    private ApiClient api;
    private final UUID employeeA = UUID.randomUUID();
    private final UUID employeeB = UUID.randomUUID();

    @BeforeEach
    void seedEntries() throws Exception {
        api = new ApiClient(port, users, encoder);
        entry(employeeA, "EMPLOYEE", "CREATE", null, "{\"jobTitle\":\"Engineer\"}", "2026-03-01T10:00:00Z");
        entry(employeeA, "EMPLOYEE", "UPDATE", "{\"jobTitle\":\"Engineer\"}", "{\"jobTitle\":\"Senior Engineer\"}", "2026-03-10T10:00:00Z");
        entry(employeeA, "COMPENSATION_RECORD", "REVISE", "{\"amount\":{\"amount\":\"1.00\",\"currency\":\"AUD\"}}",
                "{\"amount\":{\"amount\":\"2.00\",\"currency\":\"AUD\"}}", "2026-03-20T10:00:00Z");
        entry(employeeB, "EMPLOYEE", "CREATE", null, "{\"jobTitle\":\"Analyst\"}", "2026-03-15T10:00:00Z");
        entry(null, "EXCHANGE_RATE", "UPDATE", "{\"rate\":\"0.6\"}", "{\"rate\":\"0.7\"}", "2026-03-25T10:00:00Z");
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM audit_entry WHERE user_id = ?", api.userId().toString());
        jdbc.update("DELETE FROM app_user WHERE id = ?", api.userId().toString());
    }

    private void entry(UUID subject, String type, String operation, String before, String after, String at) {
        jdbc.update(
                "INSERT INTO audit_entry (id, user_id, user_email, entity_type, entity_id, subject_employee_id, operation,"
                        + " before_json, after_json, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), api.userId().toString(), api.email(), type, UUID.randomUUID().toString(),
                subject == null ? null : subject.toString(), operation, before, after, Timestamp.from(Instant.parse(at)));
    }

    private List<String> operations(Reply reply) {
        return reply.body().get("items").valueStream().map(i -> i.get("operation").asString()).toList();
    }

    @Test
    void anEmployeesTrailListsOnlyTheirEntriesNewestFirstWithBeforeAndAfter() throws Exception {
        Reply reply = api.get("/api/v1/employees/" + employeeA + "/audit");

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.body().get("totalElements").asInt()).isEqualTo(3);
        assertThat(operations(reply)).containsExactly("REVISE", "UPDATE", "CREATE");
        JsonNode update = reply.body().get("items").get(1);
        assertThat(update.get("userEmail").asString()).isEqualTo(api.email());
        assertThat(update.get("userId").asString()).isEqualTo(api.userId().toString());
        assertThat(update.get("entityType").asString()).isEqualTo("EMPLOYEE");
        assertThat(update.get("createdAt").asString()).isEqualTo("2026-03-10T10:00:00Z");
        assertThat(update.get("before").get("jobTitle").asString()).isEqualTo("Engineer");
        assertThat(update.get("after").get("jobTitle").asString()).isEqualTo("Senior Engineer");
        assertThat(reply.body().get("items").get(2).get("before").isNull()).isTrue();
    }

    @Test
    void theOrganisationTrailIncludesEveryone() throws Exception {
        Reply reply = api.get("/api/v1/audit?userId=" + api.userId());

        assertThat(reply.body().get("totalElements").asInt()).isEqualTo(5);
        assertThat(operations(reply).get(0)).isEqualTo("UPDATE"); // the exchange rate edit, 25 March
    }

    @Test
    void filtersApplyTogether() throws Exception {
        Reply reply = api.get("/api/v1/audit?userId=" + api.userId() + "&entityType=EMPLOYEE&from=2026-03-05&to=2026-03-15");

        assertThat(reply.body().get("totalElements").asInt()).isEqualTo(2);
        assertThat(operations(reply)).containsExactly("CREATE", "UPDATE"); // B on the 15th, A on the 10th
    }

    @Test
    void pagesUseTheStandardEnvelope() throws Exception {
        Reply reply = api.get("/api/v1/audit?userId=" + api.userId() + "&size=2&page=1");

        assertThat(reply.body().get("items")).hasSize(2);
        assertThat(reply.body().get("page").asInt()).isEqualTo(1);
        assertThat(reply.body().get("size").asInt()).isEqualTo(2);
        assertThat(reply.body().get("totalPages").asInt()).isEqualTo(3);
    }

    @Test
    void badFiltersAreRejectedWithASpecificCode() throws Exception {
        assertThat(api.get("/api/v1/audit?entityType=SALARY").body().get("code").asString()).isEqualTo("INVALID_FILTER");
        assertThat(api.get("/api/v1/audit?size=1000").body().get("code").asString()).isEqualTo("PAGE_SIZE_TOO_LARGE");
        assertThat(api.get("/api/v1/employees/" + employeeA + "/audit?page=-1").status()).isEqualTo(400);
    }

    @Test
    void anEmployeeIdThatIsNotAnIdHasAnEmptyTrail() throws Exception {
        Reply reply = api.get("/api/v1/employees/not-an-id/audit");

        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.body().get("totalElements").asInt()).isZero();
    }

    @Test
    void offersNoWayToChangeOrRemoveEntries() throws Exception {
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE")) {
            Reply reply = api.withToken(method, "/api/v1/audit", "{}");
            assertThat(reply.status()).as(method).isIn(404, 405);
        }
    }

    @Test
    void requiresASignedInUser() throws Exception {
        assertThat(api.withoutToken("GET", "/api/v1/audit", null).status()).isEqualTo(401);
        assertThat(api.withoutToken("GET", "/api/v1/employees/" + employeeA + "/audit", null).status()).isEqualTo(401);
    }
}
